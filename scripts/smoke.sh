#!/usr/bin/env bash
# Smoke test for the Inversiones backend API.
#
# Proves the stack actually works end to end: the app starts and reports healthy, the seeded
# local user can log in, an authenticated call to a real endpoint returns sane data, and an
# unauthenticated call to the same kind of endpoint is rejected (so this test cannot pass against
# an unsecured deployment).
#
# Usage:
#   scripts/smoke.sh [base_url]
#   BASE_URL=https://example.com scripts/smoke.sh
#
# Reused as-is by Plan 4's pipeline against a deployed environment, hence the base URL being
# fully configurable and nothing here assuming a local Docker network.
set -euo pipefail

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"
SMOKE_USERNAME="${SMOKE_USERNAME:-david}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Inversiones2026!}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-90}"

fail() {
    echo "SMOKE FAILED at step: $1" >&2
    echo "Reason: $2" >&2
    exit 1
}

echo "==> Target: ${BASE_URL}"

# --- Step 1: wait for /actuator/health to report UP, with a bounded timeout. ---------------------
echo "==> Step 1: waiting for ${BASE_URL}/actuator/health to report UP (timeout ${HEALTH_TIMEOUT_SECONDS}s)"
elapsed=0
health_ok=false
while [ "$elapsed" -lt "$HEALTH_TIMEOUT_SECONDS" ]; do
    health_body="$(curl -sf "${BASE_URL}/actuator/health" 2>/dev/null || true)"
    if [ -n "$health_body" ] && echo "$health_body" | grep -q '"status":"UP"'; then
        health_ok=true
        break
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
if [ "$health_ok" != "true" ]; then
    fail "health check" "service did not report UP within ${HEALTH_TIMEOUT_SECONDS}s (last response: ${health_body:-<none>})"
fi
echo "    health: UP (after ${elapsed}s)"

# --- Step 2: log in with the seeded local credentials and extract the bearer token. ---------------
echo "==> Step 2: logging in as '${SMOKE_USERNAME}'"
login_response="$(curl -sf -X POST "${BASE_URL}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${SMOKE_USERNAME}\",\"password\":\"${SMOKE_PASSWORD}\"}")" \
    || fail "login" "POST /api/v1/auth/login did not return 2xx"

token="$(echo "$login_response" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)"
if [ -z "$token" ]; then
    fail "login" "no token found in response: ${login_response}"
fi
echo "    token acquired"

# --- Step 3: call a protected endpoint WITH the token and assert the response shape. --------------
echo "==> Step 3: GET /api/v1/portfolio with a valid token"
portfolio_status="$(curl -s -o /tmp/smoke_portfolio_body.$$ -w '%{http_code}' \
    "${BASE_URL}/api/v1/portfolio" -H "Authorization: Bearer ${token}")"
portfolio_body="$(cat /tmp/smoke_portfolio_body.$$)"
rm -f /tmp/smoke_portfolio_body.$$

if [ "$portfolio_status" != "200" ]; then
    fail "authenticated portfolio call" "expected HTTP 200, got ${portfolio_status}: ${portfolio_body}"
fi
if ! echo "$portfolio_body" | grep -q '"baseCurrency"'; then
    fail "authenticated portfolio call" "response did not declare a baseCurrency: ${portfolio_body}"
fi
if ! echo "$portfolio_body" | grep -Eq '"totalInvested"|"totalMarketValue"'; then
    fail "authenticated portfolio call" "response did not contain any amounts: ${portfolio_body}"
fi
echo "    portfolio response declares a base currency and contains amounts"

# --- Step 4: call the SAME protected endpoint WITHOUT a token and assert it is rejected. ----------
echo "==> Step 4: GET /api/v1/portfolio with no token (must be rejected)"
unauth_status="$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/api/v1/portfolio")"
if [ "$unauth_status" != "401" ] && [ "$unauth_status" != "403" ]; then
    fail "unauthenticated portfolio call" "expected HTTP 401 or 403, got ${unauth_status} (endpoint is not actually secured)"
fi
echo "    unauthenticated call correctly rejected with HTTP ${unauth_status}"

echo "==> SMOKE OK: health, login, authenticated call and security rejection all verified against ${BASE_URL}"
