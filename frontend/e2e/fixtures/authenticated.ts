import fs from 'node:fs'
import { test as base } from '@playwright/test'
import { AUTH_FILE } from '../support/authFile'
import { AUTH_STORAGE_KEY } from '../support/credentials'

/**
 * A `test` whose `page` fixture arrives already authenticated: every
 * spec importing this gets a fresh page with the once-per-run login
 * session (captured by `auth.setup.ts`) pre-seeded into `sessionStorage`
 * via `addInitScript`, run before any app script — so `AuthProvider`
 * reads a valid session on its very first render and no spec repeats
 * the login UI flow itself.
 */
export const test = base.extend({
  page: async ({ page }, use) => {
    const session = fs.readFileSync(AUTH_FILE, 'utf-8')
    await page.addInitScript(
      ({ storageKey, value }) => {
        window.sessionStorage.setItem(storageKey, value)
      },
      { storageKey: AUTH_STORAGE_KEY, value: session },
    )
    await use(page)
  },
})

export { expect } from '@playwright/test'
