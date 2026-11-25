#!/usr/bin/env bash
# Refuses to deploy to production with a secret that came from this repository.
#
# jenkins/casc/jenkins.yaml seeds every credential with a default so JCasC boots on a laptop with
# no .env — `${JWT_SECRET_CI:-ci-placeholder-secret-at-least-32-bytes}` and
# `${POSTGRES_PASSWORD_CI:-inversiones_local}`. The reasoning was that a missing credential id
# would fail the vps branch for the wrong reason.
#
# It does not fail. deploy/docker-compose.prod.yml is evaluated on the JENKINS side, where
# deploy/.env is git-ignored and absent, and the VPS's own /docker/inversiones/.env is never read
# on this path — so these credential values are the only source. `${JWT_SECRET:?}` is satisfied,
# the placeholder is 36 bytes so JwtCodecConfig's length check passes, and it is not the one
# literal string the committed-default guard knows, so that passes too.
#
# Result, if nobody sets the overrides: production JWTs signed with a secret printed in this
# repository, forgeable by anyone who can read it, and a production Postgres password of
# `inversiones_local`.
#
# This script reads values from the ENVIRONMENT by name and never prints them — only the name of
# the variable that failed.
#
#   ./scripts/assert-not-placeholder.sh JWT_SECRET POSTGRES_PASSWORD
set -uo pipefail

# Substrings that mark a value as having come from this repository rather than from a secret store.
PATTERNS=(
  'ci-placeholder'
  'inversiones_local'
  'replace-me'
  'change-me'
  'local-development'
  'changeme'
  'placeholder'
)

fail=0
for name in "$@"; do
  value="${!name:-}"
  if [ -z "$value" ]; then
    echo "FAIL: $name is empty — production requires a real value" >&2
    fail=$((fail + 1))
    continue
  fi
  lower="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
  matched=""
  for pat in "${PATTERNS[@]}"; do
    case "$lower" in *"$pat"*) matched="$pat"; break ;; esac
  done
  if [ -n "$matched" ]; then
    echo "FAIL: $name still holds a placeholder (matched '$matched')." >&2
    echo "      Set it in Jenkins credentials, or in jenkins/.env, before deploying to the VPS." >&2
    fail=$((fail + 1))
  else
    echo "ok: $name is not a known placeholder (${#value} chars)"
  fi
done

[ "$fail" -eq 0 ] || { echo "refusing to deploy with $fail repository-sourced secret(s)" >&2; exit 1; }
