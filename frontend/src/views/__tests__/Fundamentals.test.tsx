import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { server } from '../../test/msw/server'
import {
  fundamentalsNoDataResponse,
  validFundamentalsEtfResponse,
  validFundamentalsStockResponse,
} from '../../test/msw/fixtures'
import { FundamentalsPanel } from '../Fundamentals'

const API_BASE = 'http://localhost:8080/api/v1'

function mockFundamentals(ticker: string, body: Record<string, unknown>) {
  server.use(http.get(`${API_BASE}/fundamentals/${ticker}`, () => HttpResponse.json(body)))
}

function renderPanel(ticker: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <FundamentalsPanel ticker={ticker} onClose={vi.fn()} />
    </QueryClientProvider>,
  )
}

describe('the real live-captured response for a stock (GET /fundamentals/NU)', () => {
  it('parses nulled metrics without throwing and renders only the present ones', async () => {
    mockFundamentals('NU', validFundamentalsStockResponse)
    renderPanel('NU')

    const grid = await screen.findByTestId('fundamentals-metric-grid')
    // per, peg, roe are present and routed through formatDecimalString —
    // the float artifacts (roe: "30.259999999999998", peg a long
    // repeating decimal, beta "0.96430856") must render fixed-width.
    expect(within(grid).getByText('20.97')).toBeInTheDocument()
    expect(within(grid).getByText('0.37')).toBeInTheDocument()
    expect(within(grid).getByText('30.26')).toBeInTheDocument()
    expect(within(grid).getByText('0.96')).toBeInTheDocument()

    // revenueCagr5y, epsCagr5y, dividendYield are null for this ticker —
    // no row for them at all, not a blank or a "0.00".
    expect(screen.queryByTestId('fundamentals-metric-revenueCagr5y')).not.toBeInTheDocument()
    expect(screen.queryByTestId('fundamentals-metric-epsCagr5y')).not.toBeInTheDocument()
    expect(screen.queryByTestId('fundamentals-metric-dividendYield')).not.toBeInTheDocument()
  })
})

describe('the real live-captured response for an ETF (GET /fundamentals/QQQ): hasData true, 11 of 13 metrics null', () => {
  it('does not render an empty P/E grid for the fund — only the fund-appropriate, present metric', async () => {
    mockFundamentals('QQQ', validFundamentalsEtfResponse)
    renderPanel('QQQ')

    const grid = await screen.findByTestId('fundamentals-metric-grid')
    // Only beta is present and fund-appropriate; the stock-only multiples
    // (per, peg, roe, etc.) must not appear even though hasData is true.
    expect(within(grid).getByText('1.24')).toBeInTheDocument()
    expect(screen.queryByTestId('fundamentals-metric-per')).not.toBeInTheDocument()
    expect(screen.queryByTestId('fundamentals-metric-peg')).not.toBeInTheDocument()
    expect(screen.queryByTestId('fundamentals-metric-roe')).not.toBeInTheDocument()
    expect(screen.queryByTestId('fundamentals-metric-dividendYield')).not.toBeInTheDocument()
  })
})

describe('an ETF with a non-null stock-only metric (synthetic — proves the isEtf filter itself, not just null-filtering)', () => {
  it('still hides the stock-only metric even though it is present, because the instrument is a fund', async () => {
    // Every stock-only metric in the real QQQ capture happens to be null,
    // so a test built only on that fixture cannot tell "filtered by
    // isEtf" apart from "filtered because it's null" — a broken
    // `metricsFor` that ignores `isEtf` entirely would still pass against
    // it. This fixture gives the ETF a non-null `per` to force that
    // distinction: if the component renders it, the isEtf filter is
    // gone, not just null-filtering.
    mockFundamentals('QQQ', { ...validFundamentalsEtfResponse, per: '18.50' })
    renderPanel('QQQ')

    await screen.findByTestId('fundamentals-metric-grid')
    expect(screen.queryByTestId('fundamentals-metric-per')).not.toBeInTheDocument()
    expect(screen.queryByText('18.50')).not.toBeInTheDocument()
  })
})

describe('hasData: false', () => {
  it('shows the explicit no-data state and renders no metric grid at all', async () => {
    mockFundamentals('NOPE', fundamentalsNoDataResponse)
    renderPanel('NOPE')

    expect(await screen.findByText(/no hay datos de fundamentales para nope/i)).toBeInTheDocument()
    expect(screen.queryByTestId('fundamentals-metric-grid')).not.toBeInTheDocument()
  })
})

describe('GET /fundamentals/{ticker} 404s (the common case live — 14 of 17 holdings)', () => {
  it('renders the server detail, framed in Spanish, as an alert distinguishable from the hasData:false empty state', async () => {
    server.use(
      http.get(`${API_BASE}/fundamentals/MSFT`, () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Not Found',
            status: 404,
            detail: 'Could not resolve ticker: MSFT',
            instance: '/api/v1/fundamentals/MSFT',
          },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    renderPanel('MSFT')

    // "we could not look it up" is an alert with the server's own detail...
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText(/could not resolve ticker: msft/i)).toBeInTheDocument()
    // ...framed in Spanish rather than showing the raw English string alone...
    expect(within(alert).getByText(/no se pudo obtener la información de fundamentales/i)).toBeInTheDocument()

    // ...and is NOT the "we looked and there is nothing" empty state: the
    // hasData:false message and the metric grid must both be absent here.
    expect(screen.queryByText(/no hay datos de fundamentales/i)).not.toBeInTheDocument()
    expect(screen.queryByTestId('fundamentals-metric-grid')).not.toBeInTheDocument()
  })
})
