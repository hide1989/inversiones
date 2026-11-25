#!/usr/bin/env python3
"""Checks the Jenkinsfile's vps deploy as a BLOCK, not as strings somewhere in the file.

Scoping is the whole point. Twice in this repository a file-wide grep for the right string passed
on the wrong occurrence: once on a comment that explains a flag rather than the flag itself, and
once on `assert-backend-image.sh`, which the acceptance and `local` stages already call — so "the
deployed stack asserts its image" read green while the vps path had no such call at all.

Takes the RAW Jenkinsfile, not the comment-stripped copy the rest of the linter uses: that copy
removes `//` to end of line, and `export DOCKER_HOST=ssh://root@...` contains `//`, so the block
this script looks for disappears from it. Shell comments inside the block are stripped here
instead, which is what actually matters — a `# docker save ...` line must not satisfy a check.

Usage: vps-deploy-block.py <path-to-Jenkinsfile>
Exits 0 if every assertion holds, 1 otherwise, printing one line per assertion.
"""
import re
import sys

code = open(sys.argv[1]).read()
blocks = [b for b in re.findall(r"sh\s+'''(.*?)'''", code, re.S) if "DOCKER_HOST=ssh://" in b]

if len(blocks) != 1:
    print("  FAIL  expected exactly one shell block setting DOCKER_HOST=ssh://, found %d" % len(blocks))
    sys.exit(1)

# Strip shell comments inside the block: a check must pass on a command, never on a comment that
# names it. This is the same defect that made the first version of lint-jenkinsfile.sh validate its
# own documentation.
b = re.sub(r"^\s*#.*$", "", blocks[0], flags=re.M)
checks = [
    ("the image is SHIPPED to the VPS, not built there (docker save)",
     "docker save" in b),
    ("the shipped image is loaded on the VPS (docker load)",
     "docker load" in b),
    ("no --build on the prod compose, so Gradle never runs on the 2-CPU no-swap host",
     re.search(r"compose[^\n]*\bup\b[^\n]*--build", b) is None),
    ("the DEPLOYED stack asserts it runs the image just shipped",
     "assert-backend-image.sh inversiones-backend:${IMAGE_TAG}" in b),
    ("a placeholder SEED_PASSWORD_HASH is refused before deploying",
     re.search(r"assert-not-placeholder\.sh[^\n]*SEED_PASSWORD_HASH", b) is not None),
]

failed = 0
for description, ok in checks:
    print("  %s  %s" % ("ok  " if ok else "FAIL", description))
    failed += 0 if ok else 1

sys.exit(1 if failed else 0)
