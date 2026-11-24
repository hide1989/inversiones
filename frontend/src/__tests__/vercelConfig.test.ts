import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * `vercel.json` has no other exercise in this project. The dev server never reads it, the preview
 * server never reads it, and no unit or e2e test touches it — its first real use is a deep link in
 * production. These assertions are the only thing between a typo here and every route but `/`
 * returning 404 to whoever opens it first.
 *
 * The SPA rewrite is the one that breaks visibly: React Router resolves `/posiciones` in the
 * browser, but a static host asked for `/posiciones` looks for a file by that name, finds none, and
 * answers 404 before any JavaScript runs. Rewriting unmatched paths to `index.html` is what lets
 * the router see the URL at all.
 */
describe('vercel.json', () => {
  interface VercelConfig {
    rewrites?: Array<{ source: string; destination: string }>
    headers?: Array<{ source: string; headers: Array<{ key: string; value: string }> }>
    outputDirectory?: string
  }

  const config = JSON.parse(
    readFileSync(resolve(__dirname, '../../vercel.json'), 'utf-8'),
  ) as VercelConfig

  const headersForAllPaths = (): Map<string, string> => {
    const block = config.headers?.find((entry) => entry.source === '/(.*)')
    expect(block, 'no header block covers every path').toBeDefined()
    return new Map((block?.headers ?? []).map((header) => [header.key, header.value]))
  }

  it('rewrites every unmatched path to index.html so deep links reach the router', () => {
    const fallback = config.rewrites?.find((rewrite) => rewrite.destination === '/index.html')
    expect(fallback, 'no rewrite targets /index.html — every route but / would 404').toBeDefined()
    expect(fallback?.source).toBe('/(.*)')
  })

  it('serves the directory Vite actually builds into', () => {
    // If this drifts from vite.config.ts's output, Vercel deploys an empty site successfully.
    expect(config.outputDirectory).toBe('dist')
  })

  it('refuses to be framed, and refuses content-type sniffing', () => {
    const headers = headersForAllPaths()
    expect(headers.get('X-Frame-Options')).toBe('DENY')
    expect(headers.get('X-Content-Type-Options')).toBe('nosniff')
  })

  it('sends a referrer policy that does not leak the path cross-origin', () => {
    // The paths in this app name what the user is looking at. `strict-origin-when-cross-origin`
    // sends the origin alone to third parties, never /transacciones or /posiciones.
    expect(headersForAllPaths().get('Referrer-Policy')).toBe('strict-origin-when-cross-origin')
  })

  it('sends HSTS with a max-age of at least one year', () => {
    const value = headersForAllPaths().get('Strict-Transport-Security')
    expect(value).toBeDefined()
    const maxAge = /max-age=(\d+)/.exec(value ?? '')
    expect(maxAge, 'Strict-Transport-Security carries no max-age').not.toBeNull()
    // Asserting the number rather than the exact string: a shorter max-age is the mistake worth
    // catching, and pinning the whole string would fail on an unrelated reordering.
    expect(Number(maxAge?.[1])).toBeGreaterThanOrEqual(31_536_000)
  })

  it('denies the device permissions this application never uses', () => {
    const value = headersForAllPaths().get('Permissions-Policy')
    expect(value).toBeDefined()
    for (const feature of ['camera', 'microphone', 'geolocation']) {
      expect(value, `${feature} is not denied`).toContain(`${feature}=()`)
    }
  })
})
