/**
 * Environment preconditions, checked once before anything else runs.
 *
 * Both checks below exist because their failure mode is a suite that looks like it worked. They
 * are the kind of thing a README note does not prevent, because the person who needs the note is
 * the person who has forgotten it.
 */

const PREVIEW_PORT = 5173
const BASE_URL = `http://localhost:${PREVIEW_PORT}`
const BACKEND_URL = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080'

/** Fetch that resolves to null instead of throwing when nothing is listening. */
async function tryFetch(url: string): Promise<Response | null> {
  try {
    return await fetch(url, { signal: AbortSignal.timeout(4000) })
  } catch {
    return null
  }
}

/**
 * Fails when the server on the preview port is Vite's DEV server rather than `vite preview`.
 *
 * `playwright.config.ts` sets `reuseExistingServer: !process.env.CI`, so locally Playwright
 * adopts whatever already holds port 5173 instead of starting its own. A `npm run dev` left
 * running is therefore silently substituted for the production build the whole suite is supposed
 * to exercise — and every test passes, against the wrong artifact. `vite dev` serves unbundled
 * source with no minification or tree-shaking, so a defect that only appears in the shipped
 * bundle (a mangled identifier, a differently-replaced `import.meta.env`) cannot be caught.
 *
 * The discriminator is what the root document actually references, verified against both servers
 * rather than assumed:
 *
 *   dev      → <script src="/@vite/client"> and <script src="/src/main.tsx">
 *   preview  → <script src="/assets/index-<hash>.js">
 *
 * A status-code probe of `/@vite/client` does NOT work: `vite preview` applies the SPA fallback,
 * so that path returns index.html with HTTP 200 on both. Its content type differs
 * (text/javascript vs text/html), which is a usable second signal, but the script references say
 * plainly which artifact is being served, so they are what this reports on.
 */
async function assertPreviewNotDevServer(): Promise<void> {
  const response = await tryFetch(`${BASE_URL}/`)
  if (response === null) {
    // Nothing is listening. Playwright's own `webServer` will build and serve the bundle.
    return
  }

  const html = await response.text()
  const referencesDevClient = html.includes('/@vite/client')
  const referencesRawSource = /src="\/src\//.test(html)
  if (!referencesDevClient && !referencesRawSource) {
    return
  }

  throw new Error(
    [
      '',
      `The server on port ${PREVIEW_PORT} is Vite's DEV server, not \`vite preview\`.`,
      '',
      'This suite tests the production bundle. Because playwright.config.ts sets',
      '`reuseExistingServer` outside CI, Playwright would have adopted the dev server and every',
      'test would have passed against an artifact that is never shipped — unbundled, unminified',
      'source. That is why this fails loudly instead.',
      '',
      'Stop the dev server, then run the suite again:',
      '',
      '    # find it and stop it',
      `    lsof -nP -iTCP:${PREVIEW_PORT} -sTCP:LISTEN`,
      '    # then',
      '    npm run e2e',
      '',
      `Evidence: the document at ${BASE_URL}/ references ` +
        [referencesDevClient && '"/@vite/client"', referencesRawSource && '"/src/..."']
          .filter(Boolean)
          .join(' and ') +
        ', which only the dev server serves.',
      '',
    ].join('\n'),
  )
}

/**
 * Fails when the backend is not up.
 *
 * Playwright deliberately owns only the frontend half of the stack: a `webServer` that also had
 * to start Postgres, run migrations and boot a JVM would take minutes and would report
 * infrastructure problems as "timed out waiting for the server". The cost of that separation is
 * that a forgotten `docker compose up` turns into dozens of failures with network errors in
 * them, none of which names the actual cause. This turns it into one message.
 */
async function assertBackendIsUp(): Promise<void> {
  const response = await tryFetch(`${BACKEND_URL}/actuator/health`)
  const body = response === null ? '' : await response.text()

  if (response?.ok === true && body.includes('"status":"UP"')) {
    return
  }

  throw new Error(
    [
      '',
      `The backend is not answering at ${BACKEND_URL}.`,
      '',
      'Playwright starts the frontend only; the backend and its database must already be',
      'running. Start them with:',
      '',
      '    docker compose -f ../deploy/docker-compose.dev.yml up -d',
      '',
      'Then confirm it is healthy before re-running:',
      '',
      `    curl -s ${BACKEND_URL}/actuator/health`,
      '',
      response === null
        ? 'Evidence: nothing is listening on that port.'
        : `Evidence: it answered HTTP ${response.status} with: ${body.slice(0, 200)}`,
      '',
    ].join('\n'),
  )
}

export default async function globalSetup(): Promise<void> {
  await assertPreviewNotDevServer()
  await assertBackendIsUp()
}
