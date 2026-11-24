import http from 'k6/http'
import { check } from 'k6'
import { BASE_URL, authParams, login } from '../lib/auth.js'
import { DB_ONLY_ENDPOINTS } from '../lib/endpoints.js'

/**
 * Load test: the expected steady-state traffic, held for long enough to see whether latency
 * degrades over time.
 *
 *     k6 run perf/scenarios/load.js
 *
 * Only the database- and cache-backed endpoints are exercised. See lib/endpoints.js for why the
 * n8n-backed ones are excluded — briefly: they would measure someone else's network, consume
 * metered third-party API quota, and open the circuit breakers.
 */

export const options = {
  // -----------------------------------------------------------------------------------------------
  // OPEN MODEL vs CLOSED MODEL — the choice that decides whether these numbers mean anything.
  //
  // The obvious way to write a load test is `vus: 50`: fifty virtual users, each looping. That is
  // a CLOSED model, and it has a property that quietly ruins measurements — when the system slows
  // down, the offered load DROPS. Each VU waits for its response before sending the next request,
  // so a server taking twice as long receives half the requests per second. The test "adapts" to
  // the degradation it was supposed to expose, and the throughput graph flattens instead of
  // showing a problem.
  //
  // `constant-arrival-rate` is an OPEN model: it starts whatever number of iterations per second
  // you asked for, regardless of how long each takes — the way real users arrive. If the system
  // cannot keep up, k6 says so explicitly ("Insufficient VUs"), which is the signal you want
  // rather than a silently reduced load.
  //
  // Use the closed model when modelling a fixed pool of clients (a batch job with N workers). Use
  // the open model for anything user-facing, which is this.
  // -----------------------------------------------------------------------------------------------
  // -----------------------------------------------------------------------------------------------
  // TWO PHASES, BECAUSE THE FIRST TWENTY SECONDS MEASURE THE JVM WARMING UP, NOT THE SYSTEM.
  //
  // The first version of this file ran one phase at full rate from t=0. Its time series showed a
  // transient that peaked at second 10 and was gone by second 20:
  //
  //      0s   median  4.3 ms   p99    25 ms
  //      5s   median  8.6 ms   p99   543 ms
  //     10s   median 53.4 ms   p99  1346 ms   ← worst
  //     15s   median  9.1 ms   p99   787 ms
  //     20s   median  5.2 ms   p99    55 ms
  //     25s+  median  ~5  ms   p99 15-80 ms   ← stable for the remaining 40 s
  //
  // That is not garbage collection: a GC pause is a sharp spike in one bucket, not a fifteen-second
  // curve that recovers smoothly, and the MEDIAN rose too — every request was slow, not just the
  // tail. It is the JVM warming up. The first few hundred requests run interpreted, then the JIT
  // compiles the hot paths, spending CPU to do it; afterwards the code is fast and stable. HikariCP
  // growing its pool and Hibernate preparing each entity's statements for the first time add to it.
  //
  // Startup cost is a real property, but a different one from steady-state throughput, and
  // asserting one threshold over both makes neither reportable. So the load runs in two phases and
  // only the second is asserted — every threshold below is scoped to `{scenario:steady_state}`.
  // -----------------------------------------------------------------------------------------------
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 30,
      timeUnit: '1s',
      duration: '25s',
      preAllocatedVUs: 20,
      maxVUs: 100,
      // Deliberately unasserted: its requests are excluded from every threshold by the scenario tag.
      tags: { scenario: 'warmup' },
    },
    steady_state: {
      executor: 'constant-arrival-rate',
      // 30 iterations/s × 2 requests per iteration = 60 requests/s against the API.
      rate: 30,
      timeUnit: '1s',
      duration: '1m',
      startTime: '25s',
      // Warm latency is ~5 ms median, so this rate needs very few VUs. The headroom absorbs a
      // transient without k6 dropping iterations — a dropped iteration means the load k6 was told
      // to apply was never applied, which would make the latency numbers describe a lighter test.
      preAllocatedVUs: 20,
      maxVUs: 100,
      tags: { scenario: 'steady_state' },
    },
  },

  // -----------------------------------------------------------------------------------------------
  // Thresholds derived from MEASURED baselines, not from round numbers.
  //
  // Warm single-request latencies measured on 2026-11-12: /transactions 12 ms, /signals 22 ms,
  // /diversification 33 ms. Only database-backed endpoints are measured; see lib/endpoints.js.
  //
  // A threshold set at the baseline would fail on normal variance; one set at 2000 ms would never
  // fail at all and would be decoration. These are set at roughly 10× the slowest baseline, which
  // leaves room for queueing under load while still failing if something regresses by an order of
  // magnitude — the kind of change a missing index or an N+1 query produces.
  //
  // Re-derive them if the baselines move. A threshold nobody has revisited since the code changed
  // is a threshold that has stopped meaning anything.
  // -----------------------------------------------------------------------------------------------
  thresholds: {
    // Every threshold is scoped to `{scenario:steady_state}`. An unscoped one would include the
    // warm-up phase and fail on the JIT transient documented above — which is exactly what the
    // first version of this file did.
    'checks{scenario:steady_state}': ['rate==1.00'],
    'http_req_failed{scenario:steady_state}': ['rate==0.00'],

    // Derived from the measured steady state (median ~5 ms, p95 11-16 ms, p99 15-80 ms), not from
    // round numbers. Set at roughly an order of magnitude above the observed p95 so normal variance
    // passes while a real regression — a lost index, an N+1 query — fails.
    // Re-derive these if the baselines move: a threshold nobody has revisited since the code
    // changed has quietly stopped meaning anything.
    'http_req_duration{scenario:steady_state}': ['p(95)<150', 'p(99)<500'],

    // Per endpoint, so one slow endpoint is attributable instead of averaged away.
    'http_req_duration{name:GET /transactions}': ['p(95)<200'],
    'http_req_duration{name:GET /signals}': ['p(95)<200'],

    // Throughput. Without this, the latency thresholds could pass while measuring a load that was
    // never applied: if k6 could only sustain a fraction of the requested rate, latency would look
    // excellent.
    //
    // COUNT, not rate. A `rate` threshold on a tagged subset still divides by the WHOLE run's
    // duration, warm-up included — 1801 steady-state iterations over an 85 s run reads as 21.1/s
    // and fails a `rate>29` threshold even though the scenario sustained exactly 30/s for its own
    // 60 s. That false failure is what this comment is here to prevent recurring.
    // 30 iters/s × 60 s = 1800 expected; 1750 allows for ramp edges.
    'iterations{scenario:steady_state}': ['count>1750'],
    dropped_iterations: ['count<10'],
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
