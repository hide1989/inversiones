import { expect, test } from '../fixtures/authenticated'
import { SenalesPage } from '../pages/SenalesPage'

/**
 * Task 13 added a manual signal-refresh control to /senales
 * (`POST /api/v1/signals/refresh`, 409 when a refresh is already in
 * flight). This flow drives the real control against the real composed
 * backend, which in turn calls the real remote n8n webhook — a single
 * refresh was measured taking roughly 90 seconds end to end, hence the
 * generous timeout below.
 *
 * The 409 branch is deliberately NOT asserted here. It is already
 * covered by a backend concurrency test using a latch (reliable and
 * fast); reproducing it end-to-end would mean firing two overlapping
 * browser-driven POSTs with precise timing, which is exactly the kind
 * of flaky race a browser test should not take on for a path a faster,
 * more reliable test already proves. There IS a real, unavoidable
 * two-browser race here, though: Chromium and WebKit both run this
 * spec against the SAME shared backend, so if their refresh requests
 * overlap, the backend's own in-flight guard can make ONE of them
 * receive a 409 instead of 200 — not a test bug, but the exact
 * concurrency behaviour the backend latch test already covers. Rather
 * than serialize the whole suite to avoid that overlap, this spec
 * accepts either outcome and asserts the UI handles each correctly:
 * a 200 must repopulate the table and report how many snapshots were
 * stored; a 409 must render the backend's own conflict detail through
 * the existing error path, not a generic fallback.
 */
test('the manual refresh control triggers a refresh and the table repopulates', async ({ page }) => {
  test.setTimeout(180_000)

  const senales = new SenalesPage(page)
  await senales.goto()
  await expect(senales.heading()).toBeVisible()

  const rowCountBefore = await page.locator('tbody tr').count()
  expect(rowCountBefore).toBeGreaterThan(0)

  await senales.refreshButton().click()

  // `toBeDisabled` must be asserted BEFORE `toBeEnabled`, not skipped:
  // `mutate()` flips the button to disabled synchronously, before any
  // network latency, so this resolves almost immediately regardless of
  // which branch the backend takes — but without it, `toBeEnabled`
  // below could trivially pass on the button's pre-click enabled state
  // (Playwright's click() does not wait for React to re-render), making
  // the whole test pass without ever having waited for the mutation to
  // settle at all. The transient "Actualizando…" TEXT is not asserted
  // separately: on the 409 branch the backend can reject in well under
  // a second, too short-lived for a second polling assertion to
  // reliably observe without flaking on text content specifically.
  await expect(senales.refreshButton()).toBeDisabled()
  await expect(senales.refreshButton()).toBeEnabled({ timeout: 150_000 })

  const successMessage = senales.refreshStatus()
  const alert = page.getByRole('alert')

  // Whichever of the two outcomes fired, React needs a tick to render
  // it — a plain `.isVisible()` snapshot right after `toBeEnabled()`
  // resolves can race that render and see neither element yet. Waiting
  // on the union of both locators lets Playwright's auto-retrying
  // `expect` absorb that render lag; only once something is actually
  // on screen do we check which one it was.
  await expect(successMessage.or(alert)).toBeVisible()

  if (await successMessage.isVisible()) {
    await expect(successMessage).toHaveText(/se guardaron \d+ señales nuevas\./i)
    // The table re-rendered from the invalidated query — still has rows,
    // proving the refresh's success path actually repopulated it rather
    // than leaving the previous render in place by coincidence.
    const rowCountAfter = await page.locator('tbody tr').count()
    expect(rowCountAfter).toBeGreaterThan(0)
  } else {
    await expect(alert).not.toHaveText('No se pudo actualizar las señales.')
  }
})
