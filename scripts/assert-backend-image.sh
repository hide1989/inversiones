#!/usr/bin/env bash
# Asserts that the running backend container is the image this build produced.
#
# This exists because for seven weeks it was not. deploy/docker-compose.dev.yml gave the backend a
# `build:` section and no `image:` key, so Compose named the image after the project
# (inversiones-dev-backend:latest) — a name nothing else in the pipeline ever referenced. And
# `docker compose up` builds only when the image is ABSENT: it has no content-based staleness
# detection. So the Docker Build stage built inversiones-backend:<sha> from the new code, and the
# Acceptance Tests and Smoke Test stages then started a container from an image dated 2026-10-08
# and tested that.
#
# What that costs, concretely: delete a REST endpoint the Playwright suite exercises and commit.
# Backend, Frontend and Docker Build pass, because they compile the new code. Acceptance and Smoke
# pass too, because the browser and scripts/smoke.sh are talking to a seven-week-old binary. The
# pipeline is green and the API contract is broken — a whole test suite that cannot fail, because
# it never sees the code under test.
#
#   ./scripts/assert-backend-image.sh <expected-tag> [container]
#
# Compares image IDs, not tags: two tags can point at one image, and a tag can be moved.
set -euo pipefail

TAG="${1:?usage: assert-backend-image.sh <expected-tag> [container]}"
CONTAINER="${2:-inversiones-backend}"

if ! expected="$(docker image inspect "$TAG" --format '{{.Id}}' 2>/dev/null)"; then
  echo "FAIL: no image tagged $TAG exists — the build stage did not produce it" >&2
  exit 1
fi

if ! actual="$(docker inspect "$CONTAINER" --format '{{.Image}}' 2>/dev/null)"; then
  echo "FAIL: no container named $CONTAINER is running" >&2
  exit 1
fi

if [ "$expected" != "$actual" ]; then
  echo "FAIL: $CONTAINER is NOT running the image this build produced." >&2
  echo "  expected $TAG -> ${expected:0:19}" >&2
  echo "  running              ${actual:0:19}" >&2
  echo "  running image was built: $(docker image inspect "$actual" --format '{{.Created}}' 2>/dev/null || echo unknown)" >&2
  echo "  the container is from a different image, so every test against it proves nothing" >&2
  exit 1
fi

echo "ok: $CONTAINER is running $TAG (${expected:0:19})"
