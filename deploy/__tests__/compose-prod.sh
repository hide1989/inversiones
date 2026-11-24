#!/usr/bin/env bash
# Asserts the properties of docker-compose.prod.yml that keep it from damaging the VPS it deploys
# onto. Every check here is something that is silently fine in development and expensive in
# production, which is why none of them has a test anywhere else.
#
# Run: ./deploy/__tests__/compose-prod.sh
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
COMPOSE="$(cd "$HERE/.." && pwd)/docker-compose.prod.yml"
fail=0
pass=0

check() { # check <description> <exit-code>
  if [ "$2" -eq 0 ]; then
    printf '  ok    %s\n' "$1"
    pass=$((pass + 1))
  else
    printf '  FAIL  %s\n' "$1"
    fail=$((fail + 1))
  fi
}

if [ ! -f "$COMPOSE" ]; then
  echo "  FAIL  $COMPOSE does not exist" >&2
  exit 1
fi

# Render the file the way Docker itself would, so every assertion below is about the EFFECTIVE
# configuration rather than the YAML text. Variables are supplied because the file uses the
# ${VAR:?message} form deliberately — a missing one must stop a deploy, which also means `config`
# cannot render without them.
rendered="$(
  POSTGRES_PASSWORD=x \
  JWT_SECRET=y \
  BACKEND_HOST=api.example.test \
  SECURITY_ALLOWED_ORIGINS=https://x.vercel.app \
  docker compose -f "$COMPOSE" config 2>/dev/null
)"
check "the file renders with 'docker compose config'" $?
export RENDERED="$rendered"

# --- The memory limits. This is the whole point of the file. ----------------------------------
# The VPS has 7.8 GB and NO swap, and none of its seven existing containers declares a limit. The
# backend image sets -XX:MaxRAMPercentage=75, which reads the container's cgroup limit — so with no
# limit the JVM sizes its heap from the HOST's memory and the kernel kills something to reclaim it.
# The most likely victim is n8n, this application's only source of market data.
python3 - "$COMPOSE" <<'PY'
import re, sys
text = open(sys.argv[1]).read()
# The backend service block: from "backend:" to the next service at the same indent.
m = re.search(r'\n  backend:\n(.*?)(?=\n  [a-z_-]+:\n|\nvolumes:|\nnetworks:|\Z)', text, re.S)
sys.exit(0 if m and re.search(r'^\s+mem_limit:\s*\S+', m.group(1), re.M) else 1)
PY
check "the BACKEND service declares mem_limit" $?

python3 - "$COMPOSE" <<'PY'
import re, sys
text = open(sys.argv[1]).read()
m = re.search(r'\n  postgres:\n(.*?)(?=\n  [a-z_-]+:\n|\nvolumes:|\nnetworks:|\Z)', text, re.S)
sys.exit(0 if m and re.search(r'^\s+mem_limit:\s*\S+', m.group(1), re.M) else 1)
PY
check "the POSTGRES service declares mem_limit" $?

# --- Postgres must not face the internet ------------------------------------------------------
# The dev compose publishes 5432 on purpose so pgAdmin can reach it from the host. Carrying that
# over would expose the production database; only the backend needs it, over the compose network.
# Parse the rendered YAML rather than grepping it. `docker compose config` NORMALISES ports: a
# `- "5432:5432"` in the source becomes a mapping with `target`, `published` and `protocol` keys,
# so a regex written for the short form can never match and the check can never fail. The first
# version of this check had exactly that bug — publishing 5432 left it green, which is worse than
# having no check, because the file looked verified.
python3 - <<'PY'
import os, subprocess, sys
try:
    import yaml  # noqa: F401
except ModuleNotFoundError:
    # Fall back to a text scan of the rendered output, which is still shape-aware: look for a
    # `published:` key anywhere, since this stack is meant to publish nothing at all.
    sys.exit(0 if 'published:' not in os.environ['RENDERED'] else 1)
import yaml
doc = yaml.safe_load(os.environ['RENDERED'])
offenders = [
    name for name, svc in (doc.get('services') or {}).items()
    if (svc or {}).get('ports')
]
if offenders:
    print(f"      services publishing host ports: {', '.join(offenders)}", file=sys.stderr)
sys.exit(1 if offenders else 0)
PY
check "NO service publishes a host port (the database would face the internet)" $?

# --- Traefik wiring, verified against the VPS's actual Traefik ---------------------------------
# Checked over SSH: the container is n8n-traefik-1 on network n8n_default, entrypoints web and
# websecure, cert resolver mytlschallenge, and it runs with exposedbydefault=false.
grep -q 'traefik.enable' <<<"$rendered"
check "traefik.enable is set (Traefik runs with exposedbydefault=false)" $?

grep -q 'n8n_default' <<<"$rendered"
check "the stack joins the existing n8n_default network" $?

grep -q 'mytlschallenge' <<<"$rendered"
check "the cert resolver matches the one Traefik runs" $?

grep -qE 'entrypoints:?\s*"?web,websecure' <<<"$rendered"
check "the router listens on both entrypoints Traefik defines" $?

# The external flag is what stops `compose down` here from removing the network n8n routes through.
python3 - "$COMPOSE" <<'PY'
import re, sys
text = open(sys.argv[1]).read()
nets = text.split('\nnetworks:', 1)
sys.exit(0 if len(nets) > 1 and 'external: true' in nets[1] else 1)
PY
check "the Traefik network is referenced as external, never created" $?

# --- Secrets must be required, not defaulted --------------------------------------------------
for var in POSTGRES_PASSWORD JWT_SECRET SECURITY_ALLOWED_ORIGINS BACKEND_HOST; do
  grep -qE "\\\$\{$var:\?" "$COMPOSE"
  check "$var is required (\${$var:?...}), so a deploy that forgets it stops" $?
done

# --- The image is pinned to a commit, not floating on latest ----------------------------------
grep -qE 'image:.*\$\{IMAGE_TAG' "$COMPOSE"
check "the image tag comes from IMAGE_TAG, so a deploy is traceable to a commit" $?

printf '\n  %d passed, %d failed\n' "$pass" "$fail"
exit "$fail"
