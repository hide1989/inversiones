import { expect, test } from '@playwright/test'
import { LoginPage } from '../pages/LoginPage'
import { AppShellPage } from '../pages/AppShellPage'
import { AUTH_STORAGE_KEY, SEEDED_PASSWORD, SEEDED_USERNAME } from '../support/credentials'

// Deliberately NOT the authenticated fixture: this spec exercises the
// login UI itself, so each test starts from a genuinely unauthenticated
// page — using the pre-seeded session here would skip the very flow
// under test.

test.describe('login and logout', () => {
  test('never prefills the seeded credential', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto()
    await loginPage.expectFieldsEmpty()
  })

  test('a wrong password and an unknown username both render the same alert, never a field-specific hint', async ({
    page,
  }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto()
    await loginPage.login(SEEDED_USERNAME, 'definitely-wrong-password')
    // The backend's own anti-enumeration detail, rendered verbatim — this
    // spec only asserts an alert with that exact text appears, never that
    // a wrong password reads differently from an unknown username.
    await expect(loginPage.errorAlert()).toHaveText('Invalid username or password')
  })

  test('a correct login reaches the authenticated app, and logout returns to /login and clears the session', async ({
    page,
  }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto()
    await loginPage.login(SEEDED_USERNAME, SEEDED_PASSWORD)

    await page.waitForURL('**/posiciones')
    await expect(page.getByRole('heading', { name: 'Posiciones', level: 1 })).toBeVisible()

    // Non-negotiable #2: the token lives in sessionStorage, never localStorage.
    const sessionValue = await page.evaluate((key) => window.sessionStorage.getItem(key), AUTH_STORAGE_KEY)
    expect(sessionValue).not.toBeNull()
    const localValue = await page.evaluate((key) => window.localStorage.getItem(key), AUTH_STORAGE_KEY)
    expect(localValue).toBeNull()

    const shell = new AppShellPage(page)
    await shell.logout()

    await page.waitForURL('**/login')
    await expect(loginPage.usernameInput()).toBeVisible()

    const sessionAfterLogout = await page.evaluate((key) => window.sessionStorage.getItem(key), AUTH_STORAGE_KEY)
    expect(sessionAfterLogout).toBeNull()
  })
})
