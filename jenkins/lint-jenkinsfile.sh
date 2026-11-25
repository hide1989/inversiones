#!/usr/bin/env bash
# Validates the declarative Jenkinsfile, in two layers.
#
# Layer 1 runs without Jenkins: a checklist asserting that each guard in the pipeline is actually
# present. Every line of it exists because this project watched the corresponding command report
# success while verifying nothing, so a Jenkinsfile that loses one of these flags is a Jenkinsfile
# that goes green without testing anything.
#
# Layer 2 needs a running Jenkins and checks the Groovy against its own declarative linter, which
# is the only way to catch a syntax error before a failed build does.
#
#   ./jenkins/lint-jenkinsfile.sh                      # layer 1 only, if Jenkins is not up
#   JENKINS_URL=http://localhost:8090 ./jenkins/lint-jenkinsfile.sh
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JENKINSFILE="$ROOT/Jenkinsfile"
JENKINS_URL="${JENKINS_URL:-http://localhost:8090}"
fail=0

# The Jenkinsfile with its comments removed.
#
# This matters more than it looks. The first version of this script grepped the whole file, so
# every check passed on the COMMENT that explains the guard rather than on the guard itself.
# Removing --rerun-tasks from the gradle command left the script green, because the comment above
# that command names the flag in prose. A linter validating its own documentation is worse than no
# linter: the file looks verified.
CODE="$(mktemp)"
FAILMARK="$(mktemp)"
trap 'rm -f "$CODE" "$FAILMARK"' EXIT
# Strip /* */ blocks first, then // to end of line. Crude for Groovy in general — it would also
# cut a // inside a string literal — but this file has no such string, and erring toward removal
# is the safe direction here: a stripped-away command fails its check loudly.
perl -0777 -pe 's{/\*.*?\*/}{}gs; s{//.*$}{}gm' "$JENKINSFILE" > "$CODE"

need() { # need <description> <grep-args...>
  local desc="$1"; shift
  if grep -qE "$@" "$CODE"; then
    printf '  ok    %s\n' "$desc"
  else
    printf '  FAIL  %s\n' "$desc"
    fail=$((fail + 1))
  fi
}

echo "Layer 1 — the guards must be present"

# Gradle reports "BUILD SUCCESSFUL" from up-to-date tasks without running a single test.
need "gradle build forces a real run (--rerun-tasks)" -- '--rerun-tasks'
# With allowEmptyResults: true, a build whose tests never ran publishes nothing and goes green.
need "an empty JUnit result set fails the build" -- 'allowEmptyResults: false'
# vitest prints "Tests N passed" even when a whole file failed to compile, and emits ANSI escapes
# inside "Test Files" so a grep for it returns empty.
need "vitest failures are read from a JSON report" -- 'numFailedTestSuites'
# .vitest/json/output.json is written by a command proxy, not by vitest, and goes stale silently.
need "the vitest report is written by this run (--outputFile)" -- '--outputFile'
need "the smoke stage calls the real script" -- 'scripts/smoke\.sh'
need "the frontend deploys only on the vps target" -- "DEPLOY_TARGET == 'vps'"
need "the dev stack is torn down even on failure" -- 'down -v \|\| true'
need "the image is tagged with the commit, not latest" -- 'inversiones-backend:\$\{IMAGE_TAG\}'
# A report directory that is never written would otherwise publish an empty artifact that looks
# like a passing report.
need "published reports are required to exist" -- 'allowMissing: false'
need "the merged JaCoCo path is the one that exists" -- 'reports/jacocoMergedReport/html'
# For seven weeks the acceptance and smoke stages tested an image dated 2026-10-08, because the dev
# compose named no image and `docker compose up` rebuilds only when one is ABSENT. Without this
# assertion the whole Playwright suite is a test that cannot fail.
need "the tested stack is asserted to run the image just built" -- 'assert-backend-image\.sh'
# The CasC credentials default to values committed in this repository, and the prod compose file is
# evaluated on the Jenkins side, so without this the vps branch would deploy with them.
need "the vps deploy refuses repository-sourced secrets" -- 'assert-not-placeholder\.sh'
# These suites existed and nothing ran them. The production-Postgres-port check is the highest-value
# assertion in this repository and it ran only when somebody typed it by hand.
need "the pipeline runs the compose guards" -- 'deploy/__tests__/compose-prod\.sh'
need "the pipeline runs the Jenkins security guards" -- 'jenkins/__tests__/jenkins-security\.sh'
need "the pipeline runs the backup guards" -- 'deploy/__tests__/backup-script\.sh'

# Paths the pipeline publishes must match reality. Checked here rather than trusted, because the
# plan named a jacoco path that does not exist.
echo
echo "Layer 1b — every path the pipeline publishes must exist after a real build"
# This layer used to print WARN and never touch $fail, while the Task 4 ruling claimed "the linter
# asserts each path exists on disk". It did not. It also checked two of the three publishHTML
# targets and hardcoded them, so moving a path in the Jenkinsfile left the linter checking the old
# location and still passing.
#
# The paths are now READ OUT of the Jenkinsfile, so they cannot drift from it, and a missing one
# fails. The skip is narrow and explicit: no build output at all means "you have not built yet",
# which is a different statement from "the path is wrong".
# A while-read loop, not `mapfile`: mapfile is bash 4+ and macOS ships bash 3.2 as /bin/bash.
# Worse than failing, it failed QUIETLY — `mapfile: command not found` followed by an unbound
# variable, printed to stderr while the suite carried on and reported nothing. A portability bug
# that turns a check off is the same defect this layer was rewritten to fix.
REPORT_DIRS=""
while IFS= read -r line; do
  REPORT_DIRS="$REPORT_DIRS$line
"
done < <(grep -oE "reportDir:\s*'[^']+'" "$CODE" | sed "s/.*'\(.*\)'/\1/")
REPORT_COUNT="$(printf '%s' "$REPORT_DIRS" | grep -c . || true)"

if [ "$REPORT_COUNT" -eq 0 ]; then
  printf '  FAIL  no reportDir found in the Jenkinsfile — publishHTML was removed?\n'
  fail=$((fail + 1))
elif [ ! -d "$ROOT/backend/build" ] && [ ! -d "$ROOT/frontend/node_modules" ]; then
  printf '  SKIP  nothing has been built in this tree yet; %s published path(s) unchecked\n' \
    "$REPORT_COUNT"
else
  printf '%s' "$REPORT_DIRS" | while IFS= read -r d; do
    [ -n "$d" ] || continue
    if [ -e "$ROOT/$d" ]; then
      printf '  ok    %s\n' "$d"
    else
      printf '  FAIL  %s does not exist — publishHTML has allowMissing:false and will fail the build\n' "$d"
      echo "x" >> "$FAILMARK"
    fi
  done
  # The loop above runs in a subshell (it is on the right of a pipe), so `fail=$((fail+1))` inside
  # it would be discarded — a counter that increments and vanishes is a check that cannot fail.
  # The marker file is how the count crosses back out.
  if [ -s "$FAILMARK" ]; then
    fail=$((fail + $(grep -c . "$FAILMARK")))
  fi
fi

echo
if ! curl -sf "$JENKINS_URL/login" >/dev/null 2>&1; then
  echo "Layer 2 — skipped: no Jenkins at $JENKINS_URL"
  echo "  start it with: docker compose -f jenkins/docker-compose.yml --env-file jenkins/.env up -d"
  exit "$fail"
fi

echo "Layer 2 — Jenkins' own declarative linter at $JENKINS_URL"

# Layer 2 AUTHENTICATES. It did not, and it worked anyway — because Jenkins was configured with no
# authorizationStrategy and answered anonymous callers as an administrator. Closing that hole broke
# this layer, which is the correct direction: a linter that only works against an unsecured Jenkins
# is not a linter you want working.
#
# Credentials come from jenkins/.env (git-ignored, the same file that starts the container) or from
# JENKINS_USER / JENKINS_PASSWORD in the environment. No credential is ever echoed here.
ENV_FILE="$ROOT/jenkins/.env"
if [ -z "${JENKINS_USER:-}" ] && [ -f "$ENV_FILE" ]; then
  # Anchored to the start of the line: an earlier version of this grep matched the template's
  # explanatory COMMENT and read a 97-character sentence as the password.
  JENKINS_USER="$(grep -E '^JENKINS_ADMIN_ID=' "$ENV_FILE" | cut -d= -f2-)"
  JENKINS_PASSWORD="$(grep -E '^JENKINS_ADMIN_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
fi
JENKINS_USER="${JENKINS_USER:-admin}"

if [ -z "${JENKINS_PASSWORD:-}" ]; then
  echo "  SKIPPED: no credentials. Set JENKINS_USER/JENKINS_PASSWORD, or keep jenkins/.env."
  echo "  Layer 2 cannot run against a secured Jenkins without them."
  fail=$((fail + 1))
  exit "$fail"
fi

JAR="$(mktemp)"
trap 'rm -f "$CODE" "$JAR"' EXIT
CRUMB="$(curl -sf -c "$JAR" -u "$JENKINS_USER:$JENKINS_PASSWORD" \
  "$JENKINS_URL/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)" 2>/dev/null || true)"
if [ -z "$CRUMB" ]; then
  echo "  FAIL  could not obtain a CSRF crumb — credentials rejected, or Jenkins is not ready"
  fail=$((fail + 1))
  exit "$fail"
fi
RESULT="$(curl -sf -X POST -b "$JAR" -c "$JAR" -u "$JENKINS_USER:$JENKINS_PASSWORD" -H "$CRUMB" \
  -F "jenkinsfile=<$JENKINSFILE" "$JENKINS_URL/pipeline-model-converter/validate" 2>&1)"
echo "  ${RESULT:-(empty response)}"
grep -q "successfully validated" <<<"$RESULT" || fail=$((fail + 1))

exit "$fail"
