import fs from 'node:fs'
import path from 'node:path'
import { test as setup } from '@playwright/test'
import { LoginPage } from './pages/LoginPage'
import { AUTH_FILE } from './support/authFile'
import { AUTH_STORAGE_KEY, SEEDED_PASSWORD, SEEDED_USERNAME } from './support/credentials'

/**
 * Logs in through the real UI exactly once per Playwright run (this
 * project is every other project's dependency) and persists the
 * resulting sessionStorage entry to disk.
 *
 * This is NOT Playwright's built-in `storageState()`: that API captures
 * cookies and `localStorage`, but this app deliberately keeps its token
 * in `sessionStorage` (AuthProvider.tsx) so it cannot outlive the tab —
 * `storageState()` would silently capture nothing useful. Each spec's
 * `page` fixture (see `fixtures/authenticated.ts`) seeds this same value
 * into a fresh page's `sessionStorage` via `addInitScript` before its
 * first navigation, so the UI login flow itself still runs only once.
 */
setup('authenticate', async ({ page }) => {
  const loginPage = new LoginPage(page)
  await loginPage.goto()
  await loginPage.login(SEEDED_USERNAME, SEEDED_PASSWORD)
  await page.waitForURL('**/posiciones')

  const session = await page.evaluate((key) => window.sessionStorage.getItem(key), AUTH_STORAGE_KEY)
  if (session === null) {
    throw new Error('Login succeeded but sessionStorage carries no auth entry — cannot seed other specs.')
  }

  fs.mkdirSync(path.dirname(AUTH_FILE), { recursive: true })
  fs.writeFileSync(AUTH_FILE, session, 'utf-8')
})
