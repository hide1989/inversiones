#!/usr/bin/env bash
# Daily logical backup of the production database, run by cron on the VPS.
#
# Writes a compressed dump, keeps 14 of them, and refuses to accept a dump that looks wrong — an
# empty or truncated dump that is kept and rotated is worse than no backup, because it looks like
# one until the day it is needed.
#
# THREE defences, because each catches what the others cannot:
#
#   1. `set -o pipefail` on the dump line. Without it the exit status is gzip's, so a pg_dump that
#      died would still leave a small, VALID gzip file and a zero exit status. This is the primary
#      check; the two below catch the cases where pg_dump succeeds and still gives a bad dump.
#   2. An absolute floor (MIN_BYTES). Catches a dump that carries headers but no schema. Measured
#      on this project's database: an EMPTY database dumps to 377 bytes, and the full 8-table
#      schema with no rows dumps to ~4 KB. The floor is 2 KB, which sits between them.
#   3. A relative check against the newest previous backup (MIN_RATIO_PCT). This is the one that
#      matters in production, and the reason the absolute floor alone is not enough: once the
#      database holds real data, a dump that loses its data but keeps its schema is still far
#      above any fixed floor. Measured against yesterday, it is not.
#
# All three only work because no partial file is ever left under the backup name. The first version
# of this script left one: when pg_dump failed, `set -e` aborted at the dump line BEFORE the size
# checks, so a near-empty .sql.gz stayed in the directory. The next run then read it as "the
# newest previous backup", measured against a near-zero baseline, and defence 3 was silently
# disabled — the exact failure this script exists to prevent, left open by the script itself.
# The trap below is what closes it, and it was found by running the script against a container
# name that does not exist rather than by reading it.
set -euo pipefail

CONTAINER="${CONTAINER:-inversiones-postgres}"
DB="${POSTGRES_DB:-inversiones}"
USER="${POSTGRES_USER:-inversiones}"
DEST="${DEST:-/var/backups/inversiones}"
KEEP="${KEEP:-14}"
MIN_BYTES="${MIN_BYTES:-2048}"
# A healthy dump grows or holds steady. Half of the previous one means something was lost.
MIN_RATIO_PCT="${MIN_RATIO_PCT:-50}"

size_of() { stat -c %s "$1" 2>/dev/null || stat -f %z "$1"; }

# The newest existing backup, BEFORE this run writes anything. Empty on the very first run, which
# is why the relative check is skipped rather than failed when there is nothing to compare to.
previous="$(ls -1t "$DEST"/inversiones-*.sql.gz 2>/dev/null | head -1 || true)"
prev_size=0
[ -n "$previous" ] && prev_size="$(size_of "$previous")"

mkdir -p "$DEST"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
out="$DEST/inversiones-$stamp.sql.gz"

# The stamp has second resolution, so two runs in the same second would resolve to the same name
# and the second would overwrite the first. From cron, once a night, that never happens; by hand,
# a run started right after another would silently replace a dump that might be the only good one
# on disk. Never overwrite — pick the next free suffix instead.
n=2
while [ -e "$out" ]; do
  out="$DEST/inversiones-$stamp-$n.sql.gz"
  n=$((n + 1))
done

# Until the checks below pass, $out is a partial file. If anything exits before that — a failed
# pg_dump, a full disk, a SIGTERM from cron's timeout — the trap moves it out of the way of the
# *.sql.gz glob, so it can never become the next run's baseline or sit in the keep set.
accepted=0
cleanup() {
  [ "$accepted" -eq 1 ] && return 0
  [ -e "$out" ] || return 0
  mv "$out" "$out.partial" 2>/dev/null || true
  echo "backup aborted before validation; partial file kept as $out.partial" >&2
}
trap cleanup EXIT

docker exec "$CONTAINER" pg_dump -U "$USER" -d "$DB" --clean --if-exists | gzip > "$out"

size="$(size_of "$out")"

reject() { # reject <reason>
  echo "backup $out rejected: $1" >&2
  # Renamed, not deleted: a failed backup stays on disk for a human to look at. The rotation glob
  # below does not match *.suspect, so the next run will not quietly clean it away either.
  mv "$out" "$out.suspect"
  accepted=1   # handled: .suspect is this function's own doing, not the trap's job
  exit 1
}

[ "$size" -lt "$MIN_BYTES" ] && reject "$size bytes is below the floor of $MIN_BYTES"

if [ "$prev_size" -gt 0 ]; then
  floor=$(( prev_size * MIN_RATIO_PCT / 100 ))
  [ "$size" -lt "$floor" ] && reject "$size bytes is under $MIN_RATIO_PCT% of the previous backup ($prev_size bytes, $(basename "$previous"))"
fi

accepted=1   # validated; from here the file is a real backup and the trap must leave it alone.

# Rotate: keep the newest $KEEP, delete the rest.
ls -1t "$DEST"/inversiones-*.sql.gz 2>/dev/null | tail -n "+$((KEEP + 1))" | while read -r old; do rm -- "$old"; done

kept="$(ls -1 "$DEST"/inversiones-*.sql.gz | wc -l | tr -d ' ')"
if [ "$prev_size" -gt 0 ]; then
  echo "backup ok: $out ($size bytes, previous $prev_size); $kept kept"
else
  echo "backup ok: $out ($size bytes, no previous backup to compare); $kept kept"
fi
