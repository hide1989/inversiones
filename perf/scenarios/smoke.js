import http from 'k6/http'
import { check, group } from 'k6'
import { BASE_URL, authParams, login } from '../lib/auth.js'
import { DB_ONLY_ENDPOINTS, UPSTREAM_ENDPOINTS } from '../lib/endpoints.js'

/**
 * Smoke test: one virtual user, one pass over every read endpoint.
 *
 * This is not a load test. Its job is to answer "does the system work at all, and is the baseline
 * where we left it?" before spending minutes on a real load scenario. Run it first, always: a load
 * test against a broken deployment produces numbers that mean nothing.
 *
 *     k6 run perf/scenarios/smoke.js
 */

export const options = {
  vus: 1,
  iterations: 1,

  // ---------------------------------------------------------------------------------------------
  // THRESHOLDS ARE WHAT MAKE THIS TEST FAIL. `check()` DOES NOT.
  //
  // This is the single most misunderstood thing about k6, and it is worth being blunt about.
  //
  // A `check()` records a pass/fail rate and prints a red cross next to a failed one — and the
  // run still exits with code 0. A script full of checks and no thresholds is a script that
  // reports problems and tells CI everything is fine. Jenkins reads the exit code, not the
  // prettiness of the summary, so such a test is decoration.
  //
  // A `threshold` is an assertion on a metric. When it is breached, k6 exits non-zero and the
  // pipeline fails. That is the difference between observing and verifying.
  //
  // `checks: ['rate==1.00']` is what connects the two: it turns "every check passed" into a
  // threshold, so a failed check does fail the run.
  // ---------------------------------------------------------------------------------------------
  thresholds: {
    // Every check must pass. Without this line, the checks below are advisory only.
    checks: ['rate==1.00'],
    // No request may fail at the HTTP level.
    http_req_failed: ['rate==0.00'],
  },
}

export function setup() {
  // Runs once, before any iteration. Its return value is handed to the default function.
  return { token: login() }
}

export default function (data) {
  const { token } = data

  group('endpoints backed by the database only', () => {
    for (const endpoint of DB_ONLY_ENDPOINTS) {
      const response = http.get(`${BASE_URL}${endpoint.path}`, authParams(token, endpoint.name))
      check(response, {
        [`${endpoint.name} → 200`]: (r) => r.status === 200,
        [`${endpoint.name} → body is not empty`]: (r) => r.body !== null && r.body.length > 0,
      })
    }
  })

  // Called ONCE each, never under load. See lib/endpoints.js for why: these reach n8n on every
  // request, consume third-party API quota, and can open the circuit breakers.
  group('endpoints backed by n8n (one call each, never under load)', () => {
    for (const endpoint of UPSTREAM_ENDPOINTS) {
      const response = http.get(`${BASE_URL}${endpoint.path}`, authParams(token, endpoint.name))
      check(response, {
        [`${endpoint.name} → 200`]: (r) => r.status === 200,
      })
    }
  })

  group('security', () => {
    // An unauthenticated call must be rejected. A performance suite is an odd place for this, but
    // it costs one request and it would catch a deployment that accidentally disabled the filter
    // chain — which would also invalidate every number this suite produces, since unauthenticated
    // requests skip JWT verification and are therefore faster.
    const unauthenticated = http.get(`${BASE_URL}/api/v1/portfolio`, {
      tags: { name: 'GET /portfolio (no token)' },
      // `http_req_failed` counts every non-2xx/3xx response as a failure, so this deliberate 401
      // would breach the `rate==0.00` threshold and fail the run for the one request that is
      // behaving correctly. Declaring the expected status tells k6 that 401 IS success here.
      // Leaving it out produced exactly that false failure the first time this script ran.
      responseCallback: http.expectedStatuses(401),
    })
    check(unauthenticated, {
      'unauthenticated request → 401': (r) => r.status === 401,
    })
  })
}
