import { expect, test } from '../fixtures/authenticated'
import { PosicionesPage } from '../pages/PosicionesPage'

/**
 * The one flow that matters most: with the TRM (exchange-rate) source
 * unavailable, the returns panel must say so and must render NO zero
 * figure in its place — the end-to-end proof that the frontend does not
 * undo the backend's Critical fix (a null exchange rate must never be
 * coerced into a `0.00` currency effect).
 *
 * Driving this by pointing the composed backend at an unreachable n8n
 * was considered and rejected: that backend container is shared with
 * other concurrent work in this session (another agent is reviewing
 * read-only views against the same running stack), and reconfiguring or
 * restarting it to break n8n connectivity would disrupt that work and
 * make this one test's setup a session-wide side effect. Intercepting
 * `GET /api/v1/portfolio/returns` at the network layer instead gives an
 * exact, deterministic reproduction of the one response shape the
 * backend actually sends when the TRM source is down
 * (`currencyEffectAvailable: false` with the three effect fields and
 * `currentRate` as `null` — confirmed against `TotalReturnResponseSchema`
 * and the live backend's normal response shape for everything else), with
 * no risk of leaving the shared backend mid-outage for anyone else.
 */
const UNAVAILABLE_RETURNS_RESPONSE = {
  segments: [
    {
      segment: 'USD_ASSETS',
      invested: { amount: '2913.2900000000', currency: 'USD' },
      marketValue: { amount: '3137.1035137772', currency: 'USD' },
      assetGain: { amount: '223.8135137772', currency: 'USD' },
    },
    {
      segment: 'TRII_ADR',
      invested: { amount: '640.5200000000', currency: 'USD' },
      marketValue: { amount: '878.2400000000', currency: 'USD' },
      assetGain: { amount: '237.7200000000', currency: 'USD' },
    },
    {
      segment: 'BVC_COLOMBIA',
      invested: { amount: '840272.4100000000', currency: 'COP' },
      marketValue: { amount: '1041000.0000000000', currency: 'COP' },
      assetGain: { amount: '200727.5900000000', currency: 'COP' },
    },
  ],
  baseRate: { base: 'USD', quote: 'COP', rate: '3568.42', rateDate: '2026-10-08' },
  currentRate: null,
  currencyEffectAvailable: false,
  exchangeEffect: null,
  assetGainInCop: null,
  totalReturnInCop: null,
}

test('with the TRM source unavailable, the returns view states the currency effect is unavailable and renders no zero', async ({
  page,
}) => {
  await page.route('**/api/v1/portfolio/returns', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(UNAVAILABLE_RETURNS_RESPONSE) })
  })

  const posiciones = new PosicionesPage(page)
  await posiciones.goto()

  const region = posiciones.currencyEffectRegion()
  await expect(region).toBeVisible()
  await expect(region).toContainText('No fue posible obtener la tasa de cambio')

  // The figures a healthy response would show (asset gain, exchange
  // effect, total return in COP) must not render AT ALL — not even as a
  // zero. If a future change reintroduced `Number(data.exchangeEffect ??
  // 0)` or similar, these testids would reappear and this assertion
  // would catch it immediately.
  await expect(page.getByTestId('asset-gain-figure')).toHaveCount(0)
  await expect(page.getByTestId('exchange-effect-figure')).toHaveCount(0)
  await expect(page.getByTestId('total-return-figure')).toHaveCount(0)

  // Belt and suspenders: no rendered "0.00"/"0,00" COP figure anywhere
  // in the whole total-return panel, in case a zero ever escaped through
  // a path these specific testids don't cover.
  const panelText = await posiciones.totalReturnPanel().innerText()
  expect(panelText).not.toMatch(/\b0[.,]00\b/)

  // The base TRM rate (independent of the live-rate outage) must still
  // render; the live "TRM actual" rate must not, since currentRate is null.
  await expect(posiciones.totalReturnPanel()).toContainText('TRM base')
  await expect(posiciones.totalReturnPanel()).not.toContainText('TRM actual')
})
