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
trap 'rm -f "$CODE"' EXIT
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

# Paths the pipeline publishes must match reality. Checked here rather than trusted, because the
# plan named a jacoco path that does not exist.
echo
echo "Layer 1b — every published report path exists after a real build"
for p in \
  "backend/build/reports/jacocoMergedReport/html/index.html" \
  "backend/applications/app-service/build/reports/cucumber/report.html"
do
  if [ -f "$ROOT/$p" ]; then
    printf '  ok    %s\n' "$p"
  else
    printf '  WARN  %s not present — run the build first, or the path is wrong\n' "$p"
  fi
done

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
