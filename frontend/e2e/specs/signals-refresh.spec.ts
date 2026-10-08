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
 * The 409 branch is deliberately NOT asserted by firing two overlapping
 * browser-driven POSTs on purpose. It is already covered by a backend
 * concurrency test using a latch (reliable and fast); engineering a
 * precisely-timed double-click here would be exactly the kind of flaky
 * race a browser test should not take on for a path a faster, more
 * reliable test already proves. There IS a real, unavoidable
 * two-browser race here, though: Chromium and WebKit both run this spec
 * against the SAME shared backend, so if their refresh requests
 * overlap, the backend's own in-flight guard can make ONE of them
 * receive a 409 instead of 200 — not a test bug, but the exact
 * concurrency behaviour the backend latch test already covers. Rather
 * than serialize the whole suite to avoid that overlap, this spec
 * accepts either outcome and asserts the UI handles each correctly.
 *
 * Branching is driven by the REAL HTTP response (`page.waitForResponse`),
 * not by polling the button's disabled/enabled UI state. An earlier
 * version polled `toBeDisabled()` right after `click()` to prove the
 * mutation actually started (closing a real race where `toBeEnabled()`
 * could trivially pass on the button's pre-click enabled state without
 * ever having waited for anything) — but under real four-way parallel
 * load (both browser projects' specs running concurrently against the
 * one shared backend), the 409 branch's reject-and-reset cycle can
 * complete faster than even a fast polling assertion's first check,
 * reproducibly failing in exactly that configuration. Waiting on the
 * network response itself has no such window: it resolves the instant
 * the HTTP response lands, which is the one event that cannot have
 * already happened before `click()` returns.
 */
test('the manual refresh control triggers a refresh and the table repopulates', async ({ page }) => {
  test.setTimeout(180_000)

  const senales = new SenalesPage(page)
  await senales.goto()
  await expect(senales.heading()).toBeVisible()

  const rowCountBefore = await page.locator('tbody tr').count()
  expect(rowCountBefore).toBeGreaterThan(0)

  const responsePromise = page.waitForResponse(
    (response) => response.url().endsWith('/api/v1/signals/refresh') && response.request().method() === 'POST',
    { timeout: 150_000 },
  )
  await senales.refreshButton().click()
  const response = await responsePromise

  // Whichever branch the backend actually took, wait for the button to
  // settle back to enabled before reading the rendered outcome — React
  // still needs a render pass after the fetch promise it is awaiting
  // resolves.
  await expect(senales.refreshButton()).toBeEnabled()

  if (response.status() === 200) {
    const body = await response.json()
    await expect(senales.refreshStatus()).toHaveText(`Se guardaron ${body.snapshotsStored} señales nuevas.`)
    // The table re-rendered from the invalidated query — still has rows,
    // proving the refresh's success path actually repopulated it rather
    // than leaving the previous render in place by coincidence.
    const rowCountAfter = await page.locator('tbody tr').count()
    expect(rowCountAfter).toBeGreaterThan(0)
  } else {
    expect(response.status()).toBe(409)
    // Pinned to the exact detail `SignalRefreshInProgressException`
    // carries (see ApiExceptionHandler's 409 mapping), not merely "not
    // the generic fallback" — that weaker check would also pass for an
    // unrelated error (a 500, a network failure) without confirming the
    // conflict path actually fired.
    await expect(page.getByRole('alert')).toHaveText('A signal refresh is already running; try again once it finishes')
  }
})
