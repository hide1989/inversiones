import http from 'k6/http'
import { fail } from 'k6'

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'

// Deliberately public in this repository: a single-user local deployment whose credential is
// committed on purpose. Never reuse this pattern for a real secret — pass it as an environment
// variable instead (`k6 run -e PASSWORD=...`).
const USERNAME = __ENV.USERNAME || 'david'
const PASSWORD = __ENV.PASSWORD || 'Inversiones2026!'

/**
 * Logs in once and returns the bearer token.
 *
 * Call this from a scenario's `setup()`, never from the default function: `setup()` runs exactly
 * once for the whole test, while the default function runs once per iteration — per virtual user,
 * per loop. Authenticating inside the default function would make every iteration pay for a BCrypt
 * verification, and BCrypt is deliberately slow (that is its purpose). The login endpoint would
 * then dominate the measurement and you would be load-testing password hashing instead of the
 * endpoints under test.
 */
export function login() {
  const response = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /auth/login' } },
  )

  if (response.status !== 200) {
    fail(
      `login failed with HTTP ${response.status}. Is the backend up?\n` +
        `  docker compose -f deploy/docker-compose.dev.yml up -d --wait\n` +
        `  body: ${String(response.body).slice(0, 200)}`,
    )
  }

  const token = response.json('token')
  if (!token) {
    fail('login returned HTTP 200 but no token field — the contract changed')
  }

  return token
}

/** Request params carrying the bearer token. `name` groups metrics per endpoint in the summary. */
export function authParams(token, name) {
  return {
    headers: { Authorization: `Bearer ${token}` },
    // Without an explicit `name` tag, k6 groups metrics by full URL. Any endpoint with a path
    // parameter would then produce one metric row per distinct value, and the summary becomes
    // unreadable. Tagging keeps one row per logical endpoint.
    tags: { name },
  }
}
