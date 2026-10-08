import { test as base, expect } from '@playwright/test'
import { test as authTest } from '../fixtures/authenticated'
import { LoginPage } from '../pages/LoginPage'
import { expectNoAccessibilityViolations } from '../support/axe'

// /login is public and unauthenticated, so it gets the plain `base`
// test rather than the pre-seeded-session fixture — this spec checks
// the exact page every real visitor who is not yet logged in sees.
base('the login route has zero accessibility violations', async ({ page }) => {
  const loginPage = new LoginPage(page)
  await loginPage.goto()
  await expect(loginPage.usernameInput()).toBeVisible()
  await expectNoAccessibilityViolations(page)
})

// The seven authenticated nav routes, each waited on a locator that only
// exists once its data has actually rendered (never just "navigation
// resolved") so axe scans the real content state, not a loading
// skeleton.
const AUTHENTICATED_ROUTES: ReadonlyArray<{ path: string; heading: string }> = [
  { path: '/posiciones', heading: 'Posiciones' },
  { path: '/senales', heading: 'Señales' },
  { path: '/transacciones', heading: 'Transacciones' },
  { path: '/diversificacion', heading: 'Diversificación' },
  { path: '/sintesis-ia', heading: 'Síntesis IA' },
  { path: '/earnings', heading: 'Earnings' },
  { path: '/watchlist', heading: 'Watchlist' },
]

for (const route of AUTHENTICATED_ROUTES) {
  authTest(`${route.path} has zero accessibility violations`, async ({ page }) => {
    await page.goto(route.path)
    await expect(page.getByRole('heading', { name: route.heading, level: 1 })).toBeVisible()
    await expectNoAccessibilityViolations(page)
  })
}
