import { defineConfig, devices } from '@playwright/test'

// Runs against the production Vite build, not the dev server — the
// bundle this `webServer` command serves is the one that ships. The
// backend is NOT started here: `docker compose -f
// deploy/docker-compose.dev.yml up -d` must already be running (the task
// brief confirms it is, healthy, at http://localhost:8080) — Playwright
// only owns the frontend half of the stack. `VITE_API_BASE_URL` is left
// unset on purpose: `client.ts`'s own fallback already points at
// http://localhost:8080, which is exactly where the composed backend
// listens.
// Port 5173, not Vite's preview default of 4173: the composed backend's
// CORS allowlist (`SECURITY_ALLOWED_ORIGINS` in
// deploy/docker-compose.dev.yml) defaults to http://localhost:5173 —
// the dev server's own default port — and that env var is shared,
// backend-side configuration this suite should not need to change (or
// restart the composed backend for) just to pick a different port. This
// still serves the production `vite preview` build, never `vite dev` —
// only the port number is borrowed.
const PREVIEW_PORT = 5173
const BASE_URL = `http://localhost:${PREVIEW_PORT}`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: 'html',
  timeout: 30_000,
  use: {
    baseURL: BASE_URL,
    // Traces and screenshots only on failure — a green run should leave
    // the report directory nearly empty.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      dependencies: ['setup'],
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
      dependencies: ['setup'],
    },
  ],
  webServer: {
    // Builds the real production bundle, then serves it with `vite
    // preview` — never the dev server, per the brief. The build is fast
    // enough (a few seconds) to run inline here rather than requiring a
    // pre-built `dist/` on disk.
    command: `npm run build && npm run preview -- --port ${PREVIEW_PORT} --strictPort`,
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
})
