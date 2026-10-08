import { expect, test } from '../fixtures/authenticated'
import { SenalesPage } from '../pages/SenalesPage'

test('sorting the signals table by its Score column reorders the rows, and toggling again restores the original order', async ({
  page,
}) => {
  const senales = new SenalesPage(page)
  await senales.goto()
  await expect(senales.heading()).toBeVisible()

  const scoreHeader = senales.columnHeaderButton('Score')
  await expect(scoreHeader).toBeVisible()

  // The view's own default is combined-score descending — assert that
  // starting state explicitly, rather than assuming it, so a changed
  // default would fail here instead of silently invalidating the rest
  // of this test.
  await expect(page.getByRole('columnheader', { name: /^Score/ })).toHaveAttribute('aria-sort', 'descending')

  const initialOrder = await senales.rowTickers().allTextContents()
  expect(initialOrder.length).toBeGreaterThan(1)

  await scoreHeader.click()
  await expect(page.getByRole('columnheader', { name: /^Score/ })).toHaveAttribute('aria-sort', 'ascending')
  const ascendingOrder = await senales.rowTickers().allTextContents()

  // A real sort must change the row order (unless every score tied,
  // which the live data does not) — this is the assertion that fails if
  // sorting silently became a no-op.
  expect(ascendingOrder).not.toEqual(initialOrder)

  await scoreHeader.click()
  await expect(page.getByRole('columnheader', { name: /^Score/ })).toHaveAttribute('aria-sort', 'descending')
  const backToDescending = await senales.rowTickers().allTextContents()

  expect(backToDescending).toEqual(initialOrder)
})
