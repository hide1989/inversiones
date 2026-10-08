import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/msw/server'
import {
  totalReturnDollarOnly,
  totalReturnPesoOnly,
  totalReturnUnavailable,
  validTotalReturnResponse,
} from '../../test/msw/fixtures'
import { TotalReturnPanel } from '../TotalReturnPanel'

const API_BASE = 'http://localhost:8080/api/v1'

function mockReturns(body: Record<string, unknown>, init?: { status?: number }) {
  server.use(
    http.get(`${API_BASE}/portfolio/returns`, () => {
      if (init?.status !== undefined && init.status >= 400) {
        return HttpResponse.json(body, {
          status: init.status,
          headers: { 'Content-Type': 'application/problem+json' },
        })
      }
      return HttpResponse.json(body)
    }),
  )
}

function renderPanel() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <TotalReturnPanel />
    </QueryClientProvider>,
  )
}

describe('the available case, from the real captured fixture', () => {
  it('renders the asset gain and the exchange effect as separate, currency-labelled figures', async () => {
    mockReturns(validTotalReturnResponse)
    renderPanel()

    // Both components appear, each labelled COP, and neither is summed:
    // the asset-gain figure and the exchange-effect figure must both be
    // present on their own, not folded into one net number.
    expect(await screen.findByText(/1,799,360\.10/)).toBeInTheDocument()
    expect(screen.getByText(/-?1,310,802\.48/)).toBeInTheDocument()

    const panel = screen.getByTestId('total-return-panel')
    const copLabels = within(panel).getAllByText(/COP/)
    expect(copLabels.length).toBeGreaterThan(1)
  })

  it('renders the total return subordinate to its two components', async () => {
    mockReturns(validTotalReturnResponse)
    renderPanel()

    await screen.findByText(/1,799,360\.10/)
    const assetGainFigure = screen.getByTestId('asset-gain-figure')
    const exchangeEffectFigure = screen.getByTestId('exchange-effect-figure')
    const totalFigure = screen.getByTestId('total-return-figure')

    // Both components are rendered with the primary-figure styling the
    // total deliberately does not get — this is the hierarchy check.
    expect(assetGainFigure.className).toContain('text-2xl')
    expect(exchangeEffectFigure.className).toContain('text-2xl')
    expect(totalFigure.className).not.toContain('text-2xl')
    expect(screen.getByText(/488,557\.62/)).toBeInTheDocument()
  })

  it('renders both exchange rates, grouped and rounded to two decimals, with their dates', async () => {
    mockReturns(validTotalReturnResponse)
    renderPanel()

    await screen.findByText(/1,799,360\.10/)
    // rate.rate is NUMERIC(38,10) on the wire ("3568.4200000000" /
    // "3199.5757230000" in the fixture) — Important 6: the raw string
    // was rendered verbatim before this fix, which would have made
    // "3568.4200000000 COP/USD" the visible text. formatDecimalString(
    // rate, 2, true) is what produces the grouped, rounded "3,568.42"
    // and "3,199.58" (the third decimal, 5, rounds 57 up to 58) below —
    // a regex loose enough to match the raw unformatted string would
    // defeat this assertion's whole purpose, so these are exact.
    expect(screen.getByText('3,568.42 COP/USD')).toBeInTheDocument()
    expect(screen.getByText('3,199.58 COP/USD')).toBeInTheDocument()
    expect(screen.getAllByText('2026-10-07').length).toBeGreaterThanOrEqual(2)
  })

  it('renders the three segments with their Spanish labels and real figures', async () => {
    mockReturns(validTotalReturnResponse)
    renderPanel()

    await screen.findByText(/1,799,360\.10/)
    expect(screen.getByText('Activos USD')).toBeInTheDocument()
    expect(screen.getByText('ADR en Trii')).toBeInTheDocument()
    expect(screen.getByText('BVC Colombia')).toBeInTheDocument()

    const bvcRow = screen.getByTestId('segment-row-BVC_COLOMBIA')
    expect(within(bvcRow).getByText(/222,227\.59/)).toBeInTheDocument()
  })

  it('labels every segment gain with its own currency, since the column mixes USD and COP', async () => {
    mockReturns(validTotalReturnResponse)
    renderPanel()

    await screen.findByText(/1,799,360\.10/)

    // USD_ASSETS gains +238.46 USD; BVC_COLOMBIA gains +222,227.59 COP.
    // Without a currency on the cell, 222,227 reads as the bigger number
    // when it is in fact the smaller contributor (~57 USD) — exactly the
    // confusion this panel exists to prevent, one level below the panel.
    const usdRow = screen.getByTestId('segment-row-USD_ASSETS')
    expect(within(usdRow).getByText(/USD\s*\+?238\.46/)).toBeInTheDocument()

    const bvcRow = screen.getByTestId('segment-row-BVC_COLOMBIA')
    expect(within(bvcRow).getByText(/COP\s*\+?222,227\.59/)).toBeInTheDocument()
  })
})

describe('the unavailable case: non-negotiable #2', () => {
  it('states the exchange rate is unavailable and renders no zero in that region', async () => {
    mockReturns(totalReturnUnavailable)
    renderPanel()

    const explanation = await screen.findByText(
      /no fue posible obtener la tasa de cambio actual.*efecto cambiario.*no se pueden calcular/is,
    )
    expect(explanation).toBeInTheDocument()

    const region = screen.getByTestId('currency-effect-region')
    // The literal absence of a zero: a fabricated "0" would restate the
    // exact lie non-negotiable #2 exists to prevent.
    expect(region.textContent ?? '').not.toMatch(/(?<![.\d])0(?![.\d])/)

    // Segments and the base rate are still unaffected and still render —
    // the backend only nulls the currency-effect fields, not the rest.
    expect(screen.getByText('Activos USD')).toBeInTheDocument()
    expect(screen.getByText('3,568.42 COP/USD')).toBeInTheDocument()
  })

  it('does not render the total return figure when the effect is unavailable', async () => {
    mockReturns(totalReturnUnavailable)
    renderPanel()

    await screen.findByTestId('currency-effect-region')
    expect(screen.queryByTestId('total-return-figure')).not.toBeInTheDocument()
    expect(screen.queryByTestId('asset-gain-figure')).not.toBeInTheDocument()
  })
})

describe('peso-only and dollar-only portfolios', () => {
  it('renders a peso-only portfolio (only BVC_COLOMBIA) correctly', async () => {
    mockReturns(totalReturnPesoOnly)
    renderPanel()

    await screen.findByText(/1,799,360\.10/)
    expect(screen.getByText('BVC Colombia')).toBeInTheDocument()
    expect(screen.queryByText('Activos USD')).not.toBeInTheDocument()
    expect(screen.queryByText('ADR en Trii')).not.toBeInTheDocument()
  })

  it('renders a dollar-only portfolio (USD_ASSETS and TRII_ADR) correctly', async () => {
    mockReturns(totalReturnDollarOnly)
    renderPanel()

    await screen.findByText(/1,799,360\.10/)
    expect(screen.getByText('Activos USD')).toBeInTheDocument()
    expect(screen.getByText('ADR en Trii')).toBeInTheDocument()
    expect(screen.queryByText('BVC Colombia')).not.toBeInTheDocument()
  })
})

describe('loading, error and empty states', () => {
  it('shows the shared loading skeleton', () => {
    mockReturns(validTotalReturnResponse)
    renderPanel()
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('shows the shared error state with the server message and a retry affordance', async () => {
    mockReturns(
      {
        type: 'about:blank',
        title: 'Error',
        status: 500,
        detail: 'El servidor falló al calcular el retorno.',
        instance: '/api/v1/portfolio/returns',
      },
      { status: 500 },
    )
    renderPanel()

    expect(await screen.findByText('El servidor falló al calcular el retorno.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reintentar/i })).toBeInTheDocument()
  })

  it('shows the shared empty state when there are no segments', async () => {
    mockReturns({ ...validTotalReturnResponse, segments: [] })
    renderPanel()

    expect(await screen.findByText(/no hay datos de retorno/i)).toBeInTheDocument()
  })
})
