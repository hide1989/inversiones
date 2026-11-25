#!/usr/bin/env bash
# Asserts that this Jenkins is not reachable by an anonymous caller, in the file AND on the wire.
#
# This suite exists because it was not. The first version of jenkins/casc/jenkins.yaml configured a
# securityRealm and no authorizationStrategy. Three invalid JCasC keys had been removed while
# getting the container to boot — among them `loggedInAuthorizationStrategy`, whose correct spelling
# is `loggedInUsersCanDoAnything` — and nothing was put back in its place. Jenkins then kept its
# default AuthorizationStrategy.Unsecured, under which every anonymous caller is an administrator,
# and JENKINS_ADMIN_PASSWORD protected nothing at all.
#
# Measured on the running container before the fix, with no credentials:
#
#     GET  /api/json                  200
#     GET  /credentials/              200
#     GET  /job/inversiones/config.xml  200
#     POST /scriptText  (+ anonymous crumb)  executed Groovy and returned its output
#
# The container mounts /var/run/docker.sock, so that last line is root-equivalent control of the
# Docker daemon that also runs the user's n8n. The port was published on 0.0.0.0.
#
# Note on how this was nearly missed: an unauthenticated POST to /scriptText answers 403, which
# reads like authorization working. It is the CSRF crumb check, and Jenkins issues a crumb to
# anonymous callers. The difference between "403" and "403 for the wrong reason" is this whole
# suite, so Layer 2 asserts a privileged GET (no crumb involved) rather than trusting the POST.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CASC="$ROOT/casc/jenkins.yaml"
COMPOSE="$ROOT/docker-compose.yml"
JENKINS_URL="${JENKINS_URL:-http://localhost:8090}"
fail=0

ok()   { printf '  ok    %s\n' "$1"; }
bad()  { printf '  FAIL  %s\n' "$1"; fail=$((fail + 1)); }

echo "Layer 1 — the configuration must deny anonymous access"

# Without this key Jenkins is Unsecured, whatever the securityRealm says.
if grep -qE "^\s*authorizationStrategy:" "$CASC"; then ok "an authorizationStrategy is declared"
else bad "no authorizationStrategy in casc/jenkins.yaml — Jenkins defaults to Unsecured"; fi

if grep -qE "^\s*loggedInUsersCanDoAnything:" "$CASC"; then ok "the strategy requires a login"
else bad "the strategy is not loggedInUsersCanDoAnything"; fi

# allowAnonymousRead: true would still expose build consoles and job config to anyone.
if grep -qE "allowAnonymousRead:\s*false" "$CASC"; then ok "anonymous read is explicitly off"
else bad "allowAnonymousRead is not false — consoles and job config stay public"; fi

# A CI controller with the Docker socket mounted must not listen on every interface.
if grep -qE '^\s*-\s*"127\.0\.0\.1:\$\{JENKINS_PORT' "$COMPOSE"; then
  ok "the HTTP port is bound to loopback, not 0.0.0.0"
else
  bad "the HTTP port is published on all interfaces while /var/run/docker.sock is mounted"
fi

echo
if ! curl -sf "$JENKINS_URL/login" >/dev/null 2>&1; then
  echo "Layer 2 — skipped: no Jenkins at $JENKINS_URL"
  exit "$fail"
fi

echo "Layer 2 — the running instance must refuse an anonymous caller"

# Privileged GETs. No crumb is involved, so a refusal here is authorization and nothing else.
for path in /api/json /credentials/ /systemInfo /job/inversiones/config.xml; do
  code="$(curl -s -o /dev/null -w '%{http_code}' "$JENKINS_URL$path")"
  case "$code" in
    401|403) ok "anonymous GET $path refused ($code)" ;;
    *)       bad "anonymous GET $path returned $code — expected 401 or 403" ;;
  esac
done

# The Groovy console, reached the way an attacker would: fetch a crumb first, then POST.
crumb="$(curl -s -c /tmp/jsec-anon.$$ "$JENKINS_URL/crumbIssuer/api/json" 2>/dev/null \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(f\"{d['crumbRequestField']}: {d['crumb']}\")" 2>/dev/null || true)"
body="$(curl -s -b /tmp/jsec-anon.$$ ${crumb:+-H "$crumb"} -X POST \
  --data-urlencode "script=println 'EXECUTED'" "$JENKINS_URL/scriptText" 2>/dev/null || true)"
rm -f /tmp/jsec-anon.$$
if grep -q "EXECUTED" <<<"$body"; then
  bad "anonymous Groovy execution SUCCEEDED — remote code execution on a container with the Docker socket"
else
  ok "anonymous Groovy execution refused"
fi

echo
if [ "$fail" -eq 0 ]; then echo "all checks passed"; else echo "$fail check(s) failed"; fi
exit "$fail"
