import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test, expect } from '@playwright/test'
import { SEEDED_PASSWORD } from '../support/credentials'

/**
 * Scans the actual built `dist/` output — not the dev server, not a
 * one-off manual grep after a local build — for secrets that must never
 * reach the client. This runs as part of every `npm run e2e`, which
 * always builds first (`playwright.config.ts`'s `webServer` command is
 * `npm run build && npm run preview`), so by the time this test runs,
 * `dist/` is guaranteed fresh for THIS run, not a stale artifact from
 * some earlier invocation. A manual grep run once and never repeated is
 * indistinguishable from a check that was never run again — this is the
 * repeatable replacement for that.
 *
 * Two distinct strings, two distinct reasons:
 *  - The JWT signing secret (`JWT_SECRET` in docker-compose.dev.yml):
 *    a real secret backing the deployed backend's tokens. It must never
 *    appear in client code under any circumstance.
 *  - The seeded login password: NOT confidential (it's deliberately
 *    public in this repo — see `support/credentials.ts`), but its
 *    presence in the bundle would mean it got hardcoded as a login-form
 *    default value, which is exactly the "never prefilled" rule this
 *    plan requires elsewhere (`auth.spec.ts`). Checking the built
 *    output catches that mistake even if it is only ever verified in
 *    the browser with the field rendered empty.
 */
const FORBIDDEN_STRINGS: ReadonlyArray<{ label: string; value: string }> = [
  { label: 'the backend JWT signing secret', value: 'local-development-secret-change-me-32bytes' },
  { label: 'the seeded login password', value: SEEDED_PASSWORD },
]

const TEXT_EXTENSIONS = new Set(['.js', '.css', '.html', '.json', '.map', '.svg', '.txt'])

const DIST_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../dist')

function listFilesRecursive(dir: string): string[] {
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  return entries.flatMap((entry) => {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) return listFilesRecursive(fullPath)
    return [fullPath]
  })
}

test('the production bundle contains no secret', () => {
  expect(fs.existsSync(DIST_DIR), `dist/ not found at ${DIST_DIR} — did the build run?`).toBe(true)

  const files = listFilesRecursive(DIST_DIR).filter((file) => TEXT_EXTENSIONS.has(path.extname(file)))
  expect(files.length, 'expected at least one built asset to scan').toBeGreaterThan(0)

  const offenders: Array<{ file: string; label: string }> = []
  for (const file of files) {
    const content = fs.readFileSync(file, 'utf-8')
    for (const forbidden of FORBIDDEN_STRINGS) {
      if (content.includes(forbidden.value)) {
        offenders.push({ file: path.relative(DIST_DIR, file), label: forbidden.label })
      }
    }
  }

  expect(offenders, JSON.stringify(offenders, null, 2)).toEqual([])
})
