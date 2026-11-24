import http from 'k6/http'
import { check } from 'k6'
import { BASE_URL, authParams, login } from '../lib/auth.js'
import { DB_ONLY_ENDPOINTS } from '../lib/endpoints.js'

/**
 * Stress test: raise the arrival rate in steps until latency degrades, to find where the system
 * stops coping.
 *
 *     k6 run perf/scenarios/stress.js
 *
 * ------------------------------------------------------------------------------------------------
 * WHY THIS SCENARIO'S THRESHOLDS ARE DELIBERATELY LOOSE
 *
 * In load.js a breached threshold is a FAILURE: the system did not meet its expected service level
 * under expected traffic, and the pipeline should stop.
 *
 * Here a breached threshold is the RESULT. The whole purpose is to push until something gives, so
 * strict thresholds would fail the run precisely when the test succeeds at its job. Copying
 * load.js's thresholds into a stress test is a common and confusing mistake — the run goes red,
 * someone "fixes" it by raising the limits, and the test stops finding anything.
 *
 * What is asserted instead is narrow and about correctness, not speed: under stress the system may
 * get slow, but it must not return wrong answers or 5xx errors. A system that degrades gracefully
 * (slower, still correct) passes. One that starts failing requests does not, and that boundary is
 * the finding worth reporting.
 *
 * `abortOnFail` stops the run once the error rate climbs, so the remaining stages do not spend
 * minutes hammering a system that has already broken.
 * ------------------------------------------------------------------------------------------------
 *
 * A LIMITATION THAT MUST BE DECLARED IN ANY REPORT USING THESE NUMBERS
 *
 * The load generator and the system under test share the same machine — 8 cores, 16 GB. Past some
 * concurrency, the numbers stop describing the application and start describing contention between
 * k6, the JVM and Postgres competing for the same cores. The symptom is latency rising while CPU
 * saturates and throughput plateaus below the requested rate.
 *
 * This does not invalidate the measurement, but it does bound its interpretation: the breaking
 * point found here is a floor, not the application's ceiling. A separate load-generator host would
 * be needed to measure the real limit.
 */

export const options = {
  scenarios: {
    ramp_to_breaking_point: {
      executor: 'ramping-arrival-rate',
      startRate: 20,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      // Generous: in an open model, k6 needs more VUs as latency rises to keep the arrival rate.
      // Hitting maxVUs is itself a signal — it means each iteration is taking long enough that the
      // pool cannot sustain the requested rate.
      maxVUs: 1000,
      stages: [
        // Each iteration issues 2 requests, so multiply by 2 for requests per second.
        { target: 30, duration: '20s' }, //    90 req/s — the load.js baseline, for comparison
        { target: 100, duration: '30s' }, //  300 req/s
        { target: 250, duration: '30s' }, //  750 req/s
        { target: 500, duration: '30s' }, // 1500 req/s
        { target: 900, duration: '30s' }, // 2700 req/s
        { target: 0, duration: '10s' }, //   ramp down, to see whether it recovers
      ],
    },
  },

  thresholds: {
    // Correctness, not speed. Under stress the system may be slow; it must not be wrong.
    // 1% is the tolerance: above that, the run aborts rather than continuing to hammer a system
    // that has already passed its breaking point.
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '10s' }],
    // Every response that DOES arrive must still be a valid 200 with a body.
    checks: ['rate>0.99'],
    // Recorded for the report, set high enough that it does not abort the ramp. Breaching this is
    // the expected outcome at the top stages and is the number the report is about.
    http_req_duration: ['p(95)<5000'],
  },
}

export function setup() {
  return { token: login() }
}

export default function (data) {
  const { token } = data

  for (const endpoint of DB_ONLY_ENDPOINTS) {
    const response = http.get(`${BASE_URL}${endpoint.path}`, authParams(token, endpoint.name))
    check(response, {
      [`${endpoint.name} → 200`]: (r) => r.status === 200,
    })
  }
}
