import { expect, test } from '../fixtures/authenticated'
import { PosicionesPage } from '../pages/PosicionesPage'

// QQQ/Hapi is part of the long-standing seeded portfolio (confirmed live
// against the composed backend) and is never touched by the
// create/edit/delete lifecycle spec, which uses its own disjoint
// ticker/broker pairs — so asserting on it here is safe even when that
// spec runs concurrently in the other browser project.
test('the positions dashboard loads and shows the seeded portfolio', async ({ page }) => {
  const posiciones = new PosicionesPage(page)
  await posiciones.goto()

  await expect(posiciones.heading()).toBeVisible()

  const seededRow = posiciones.rowFor('QQQ', 'Hapi')
  await expect(seededRow).toBeVisible()
  await expect(seededRow).toContainText('QQQ')
  await expect(seededRow).toContainText('Hapi')

  await expect(posiciones.totalReturnPanel()).toBeVisible()
})
