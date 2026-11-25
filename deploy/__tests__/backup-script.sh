#!/usr/bin/env bash
# Tests for deploy/backup/pg_dump_daily.sh — the four defences, each verified by making it fail.
#
# These exist because the guards were originally checked by hand, and a by-hand check does not
# survive the session that ran it. One of the four was found broken that way and would have
# regressed silently: see the "partial file" test below.
#
# Needs a running Postgres container with this project's schema. Skips, loudly, without one —
# a test that silently passes when its subject is absent is worse than no test.
#
#   docker compose -f deploy/docker-compose.dev.yml up -d --wait
#   ./deploy/__tests__/backup-script.sh
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="$ROOT/deploy/backup/pg_dump_daily.sh"
CONTAINER="${CONTAINER:-inversiones-postgres}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
fail=0

check() { # check <description> <expected> <actual>
  if [ "$2" = "$3" ]; then
    printf '  ok    %s\n' "$1"
  else
    printf '  FAIL  %s (expected %s, got %s)\n' "$1" "$2" "$3"
    fail=$((fail + 1))
  fi
}

if ! docker exec "$CONTAINER" true 2>/dev/null; then
  echo "SKIPPED: no container named $CONTAINER."
  echo "  start it with: docker compose -f deploy/docker-compose.dev.yml up -d --wait"
  exit 0
fi
if [ "$(docker exec "$CONTAINER" psql -U inversiones -d inversiones -tAc \
      "select count(*) from information_schema.tables where table_schema='public';" 2>/dev/null)" = "0" ]; then
  echo "SKIPPED: $CONTAINER has no schema — bring the backend up so Flyway migrates, or this"
  echo "  suite would assert against an empty database and prove nothing."
  exit 0
fi

echo "pg_dump_daily.sh"

# 1. The happy path, with nothing to compare against.
out="$(CONTAINER="$CONTAINER" DEST="$TMP/a" "$SCRIPT" 2>&1)"; rc=$?
check "a healthy dump is accepted" 0 "$rc"
check "the first run says there is no previous backup" 0 \
  "$(grep -cq 'no previous backup' <<<"$out"; echo $?)"
check "exactly one backup is kept" 1 "$(ls -1 "$TMP/a"/inversiones-*.sql.gz 2>/dev/null | wc -l | tr -d ' ')"
check "the dump carries the schema, not just headers" 0 \
  "$(gunzip -c "$TMP/a"/inversiones-*.sql.gz | grep -q '^CREATE TABLE'; echo $?)"

# 2. A second run compares against the first.
CONTAINER="$CONTAINER" DEST="$TMP/a" "$SCRIPT" >/dev/null 2>&1
check "a second healthy dump is accepted" 0 "$?"
check "both backups are kept" 2 "$(ls -1 "$TMP/a"/inversiones-*.sql.gz | wc -l | tr -d ' ')"

# 3. The absolute floor. An unreachable MIN_BYTES stands in for a dump that came back with
#    headers and no schema — measured at 377 bytes on an empty database.
CONTAINER="$CONTAINER" DEST="$TMP/b" MIN_BYTES=999999999 "$SCRIPT" >/dev/null 2>&1
check "a dump under MIN_BYTES is rejected" 1 "$?"
check "the rejected dump is kept as .suspect" 1 "$(ls -1 "$TMP/b"/*.suspect 2>/dev/null | wc -l | tr -d ' ')"
check "a rejected dump is NOT left where the glob can see it" 0 \
  "$(ls -1 "$TMP/b"/inversiones-*.sql.gz 2>/dev/null | wc -l | tr -d ' ')"

# 4. The relative check — the one that matters once the database holds real data, because a dump
#    that keeps its schema and loses its rows is still far above any fixed floor.
mkdir -p "$TMP/c"
head -c 200000 /dev/urandom > "$TMP/c/inversiones-20260101T000000Z.sql.gz"
CONTAINER="$CONTAINER" DEST="$TMP/c" "$SCRIPT" >/dev/null 2>&1
check "a dump far smaller than the previous one is rejected" 1 "$?"
check "the previous backup is left untouched" 1 \
  "$(ls -1 "$TMP/c"/inversiones-20260101T000000Z.sql.gz 2>/dev/null | wc -l | tr -d ' ')"
CONTAINER="$CONTAINER" DEST="$TMP/c" MIN_RATIO_PCT=0 "$SCRIPT" >/dev/null 2>&1
check "MIN_RATIO_PCT=0 disables the relative check" 0 "$?"

# 5. The partial file. This is the regression test for a real bug: when pg_dump failed, `set -e`
#    aborted BEFORE the size checks, leaving a near-empty .sql.gz under the backup name. The next
#    run read it as the newest previous backup, so the relative check measured against a near-zero
#    baseline and defence 4 was silently disabled.
CONTAINER="definitely-not-a-container" DEST="$TMP/d" "$SCRIPT" >/dev/null 2>&1
check "a failed pg_dump exits non-zero" 1 "$?"
check "a failed pg_dump leaves nothing the glob will accept as a backup" 0 \
  "$(ls -1 "$TMP/d"/inversiones-*.sql.gz 2>/dev/null | wc -l | tr -d ' ')"
check "the partial file is kept for inspection" 1 \
  "$(ls -1 "$TMP/d"/*.partial 2>/dev/null | wc -l | tr -d ' ')"

# 6. Rotation keeps the newest KEEP and nothing else.
mkdir -p "$TMP/e"
for i in 1 2 3 4; do
  printf 'x%.0s' $(seq 1 5000) | gzip > "$TMP/e/inversiones-2026010${i}T000000Z.sql.gz"
  sleep 0.05
done
CONTAINER="$CONTAINER" DEST="$TMP/e" KEEP=2 "$SCRIPT" >/dev/null 2>&1
check "KEEP=2 leaves two backups" 2 "$(ls -1 "$TMP/e"/inversiones-*.sql.gz | wc -l | tr -d ' ')"
check "the newest survives rotation" 0 \
  "$(ls -1t "$TMP/e"/inversiones-*.sql.gz | head -1 | grep -qv '20260101'; echo $?)"

echo
if [ "$fail" -eq 0 ]; then echo "all checks passed"; else echo "$fail check(s) failed"; fi
exit "$fail"
