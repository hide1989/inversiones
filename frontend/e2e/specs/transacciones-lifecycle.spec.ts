import { expect, test } from '../fixtures/authenticated'
import { PosicionesPage } from '../pages/PosicionesPage'
import { TransaccionesPage } from '../pages/TransaccionesPage'

/**
 * Registering a purchase, editing it, and deleting it — run as one
 * ordered story (not three independent tests) so the edit and delete
 * steps operate on the exact transaction the create step produced,
 * and so the suite leaves the shared composed backend exactly as it
 * found it once this file finishes.
 *
 * Both tickers below already resolve against the backend's instrument
 * catalog and both brokers are already known (confirmed live: unknown
 * ticker/broker both 400), but neither ticker/broker PAIR carries an
 * existing seeded position — so creating one adds a brand-new position
 * row rather than merging quantity into real seeded data.
 *
 * Chromium and WebKit run this same file against the SAME shared
 * backend instance, in parallel. Using one fixed ticker/broker pair
 * would mean both projects create, edit and delete the identical
 * position concurrently — e.g. one project's "quantity should now be 2"
 * assertion landing after the other project's edit changed it to 3. Each
 * Playwright project gets its own disjoint pair instead, keyed off
 * `testInfo.project.name`, so the two projects never touch the same row.
 */
const FIXTURE_BY_PROJECT: Record<string, { ticker: string; brokerName: string }> = {
  chromium: { ticker: 'MSFT', brokerName: 'Hapi' },
  webkit: { ticker: 'AMZN', brokerName: 'Trii' },
}

function fixtureFor(projectName: string): { ticker: string; brokerName: string } {
  const fixture = FIXTURE_BY_PROJECT[projectName]
  if (fixture === undefined) {
    throw new Error(`No disjoint ticker/broker fixture configured for Playwright project "${projectName}"`)
  }
  return fixture
}

test.describe.serial('registering, editing and deleting a transaction', () => {
  let transactionId: number

  test('registering a purchase shows it reflected in the positions dashboard', async ({ page }, testInfo) => {
    const { ticker, brokerName } = fixtureFor(testInfo.project.name)

    const transacciones = new TransaccionesPage(page)
    await transacciones.goto()
    await expect(transacciones.heading()).toBeVisible()

    await transacciones.newButton().click()
    await transacciones.fillForm({
      ticker,
      brokerName,
      type: 'BUY',
      quantity: '2',
      unitPrice: '100.00',
      currency: 'USD',
      tradedAt: '2026-10-07',
    })
    await transacciones.submitForm()

    await expect(transacciones.form()).not.toBeVisible()

    const row = page.getByRole('row', { name: new RegExp(`${ticker}.*${brokerName}`) })
    await expect(row).toBeVisible()
    const testId = await row.getAttribute('data-testid')
    expect(testId).not.toBeNull()
    transactionId = Number(testId?.replace('transaction-row-', ''))
    expect(Number.isInteger(transactionId)).toBe(true)

    const posiciones = new PosicionesPage(page)
    await posiciones.goto()
    const positionRow = posiciones.rowFor(ticker, brokerName)
    await expect(positionRow).toBeVisible()
    await expect(positionRow).toContainText('2') // formatQuantity trims "2.0000000000" to "2"
    await expect(positionRow).toContainText('100.00') // average cost: the lone BUY's own unit price
  })

  test('editing the transaction recalculates the average cost', async ({ page }, testInfo) => {
    const { ticker, brokerName } = fixtureFor(testInfo.project.name)

    const transacciones = new TransaccionesPage(page)
    await transacciones.goto()
    await transacciones.editButtonFor(transactionId).click()
    await expect(transacciones.form()).toBeVisible()

    await transacciones.fillForm({
      ticker,
      brokerName,
      type: 'BUY',
      quantity: '3',
      unitPrice: '150.00',
      currency: 'USD',
      tradedAt: '2026-10-07',
    })
    await transacciones.submitForm()
    await expect(transacciones.form()).not.toBeVisible()

    const posiciones = new PosicionesPage(page)
    await posiciones.goto()
    const positionRow = posiciones.rowFor(ticker, brokerName)
    await expect(positionRow).toContainText('3')
    // The lone transaction now prices the whole lot, so the recalculated
    // average cost is exactly the edited unit price — a figure this test
    // would catch drifting if the backend's recalculation ever regressed.
    await expect(positionRow).toContainText('150.00')
  })

  test('deleting the transaction removes it from the list and from positions', async ({ page }, testInfo) => {
    const { ticker, brokerName } = fixtureFor(testInfo.project.name)

    const transacciones = new TransaccionesPage(page)
    await transacciones.goto()
    await transacciones.deleteButtonFor(transactionId).click()
    await expect(transacciones.deleteConfirmFor(transactionId)).toBeVisible()
    await transacciones.confirmDeleteButtonFor(transactionId).click()

    await expect(transacciones.rowFor(transactionId)).not.toBeVisible()

    const posiciones = new PosicionesPage(page)
    await posiciones.goto()
    await expect(posiciones.rowFor(ticker, brokerName)).not.toBeVisible()
  })
})
