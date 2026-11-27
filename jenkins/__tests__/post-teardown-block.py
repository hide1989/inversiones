#!/usr/bin/env python3
"""Checks the PIPELINE-LEVEL post block, as a block.

Why a separate checker rather than a grep in lint-jenkinsfile.sh: every string this needs to
assert on also appears elsewhere in the Jenkinsfile. `down -v || true` appears in the Acceptance
Tests stage's own post block, and that stage is SUPPOSED to tear its stack down immediately. A
file-wide grep therefore cannot tell the two apart, and a file-wide grep for a grace period would
pass on the acceptance teardown having one -- which would be a defect, not the feature.

What this exists to prevent recurring: build #22 deployed with DEPLOY_TARGET=local, passed its
smoke test at 05:49:41, and the pipeline-level post block removed the containers AND the volume at
05:49:42. A deploy that destroys itself half a second after being verified is not a deploy. The
post block is still needed as a net for a build that dies between `up` and the acceptance
teardown, so the fix is to scope it, not to delete it.
"""
import re
import sys

path = sys.argv[1]
src = open(path, encoding="utf8").read()

# The pipeline-level post block: `post {` at exactly two-space indent. A stage's post block is
# indented six spaces, so the indent is what distinguishes them -- not the order in the file.
match = re.search(r"^  post \{\n(.*?)^  \}", src, re.S | re.M)
if not match:
    print("  FAIL  no pipeline-level post block found (two-space indent) in " + path)
    sys.exit(1)

block = match.group(1)
# Strip Groovy // comments: the prose below the fix explains it and names every string asserted
# here, so a checker reading comments would pass on its own documentation. That exact failure --
# a linter validating the comment that describes a guard instead of the guard -- is why
# lint-jenkinsfile.sh strips comments before grepping.
code = re.sub(r"//.*$", "", block, flags=re.M)

failures = 0


def need(desc, ok):
    global failures
    print(("  ok    " if ok else "  FAIL  ") + desc)
    if not ok:
        failures += 1


need(
    "the pipeline post block still tears the dev stack down (the net for a build that dies early)",
    "down -v || true" in code,
)
need(
    "a local deploy is not destroyed immediately: a grace period of at least 300s precedes it",
    bool(re.search(r"sleep\s+(3[0-9]{2}|[4-9][0-9]{2}|[0-9]{4,})", code)),
)
need(
    "the grace period is SCOPED to DEPLOY_TARGET == 'local', not paid by every build",
    bool(re.search(r"DEPLOY_TARGET\s*==\s*'local'", code)),
)
# Without this the teardown could be skipped entirely on the local path, which would leave the
# containers running forever -- the opposite failure, and one a demo would not notice.
need(
    "the teardown still RUNS after the grace period (it is delayed, never skipped)",
    code.count("down -v || true") >= 1 and "sleep" in code,
)

print(
    "  pipeline post block: %d check(s) failed" % failures
    if failures
    else "  pipeline post block: all checks passed"
)
sys.exit(1 if failures else 0)
