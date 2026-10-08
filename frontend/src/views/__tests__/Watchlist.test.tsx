import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/msw/server'
import { validFundamentalsStockResponse, validWatchlistResponse } from '../../test/msw/fixtures'
import { Watchlist } from '../Watchlist'

const API_BASE = 'http://localhost:8080/api/v1'

function mockWatchlist(body: ReadonlyArray<Record<string, unknown>>) {
  server.use(http.get(`${API_BASE}/watchlist`, () => HttpResponse.json(body)))
}

function mockFundamentals(body: Record<string, unknown>) {
  server.use(http.get(`${API_BASE}/fundamentals/:ticker`, () => HttpResponse.json(body)))
}

function renderView() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <Watchlist />
    </QueryClientProvider>,
  )
}

describe('the real live-captured watchlist (31 items, 12 null for price/technicalScore)', () => {
  it('renders every row, with a dash in place of the absent price/technical score fields', async () => {
    mockWatchlist(validWatchlistResponse)
    mockFundamentals(validFundamentalsStockResponse)
    renderView()

    const rows = await screen.findAllByTestId(/^watchlist-row-/)
    expect(rows).toHaveLength(validWatchlistResponse.length)

    const spyRow = screen.getByTestId('watchlist-row-SPY')
    expect(within(spyRow).getAllByText('—').length).toBeGreaterThanOrEqual(2)

    const amdRow = screen.getByTestId('watchlist-row-AMD')
    expect(within(amdRow).getByText('649.42')).toBeInTheDocument()
  })

  it('renders the watchlist-specific Spanish signal vocabulary verbatim, not mapped to BUY/HOLD/SELL', async () => {
    mockWatchlist(validWatchlistResponse)
    mockFundamentals(validFundamentalsStockResponse)
    renderView()

    await screen.findAllByTestId(/^watchlist-row-/)
    expect(screen.getAllByText('COMPRA').length).toBeGreaterThan(0)
    expect(screen.getAllByText('FUERTE COMPRA').length).toBeGreaterThan(0)
    expect(screen.getAllByText('MANTENER').length).toBeGreaterThan(0)
    expect(screen.getAllByText('VENTA').length).toBeGreaterThan(0)
    expect(screen.queryByText('BUY')).not.toBeInTheDocument()
    expect(screen.queryByText('HOLD')).not.toBeInTheDocument()
  })

  it('opens the fundamentals panel for a ticker on demand, not on mount', async () => {
    let fundamentalsRequestCount = 0
    mockWatchlist(validWatchlistResponse)
    server.use(
      http.get(`${API_BASE}/fundamentals/:ticker`, () => {
        fundamentalsRequestCount += 1
        return HttpResponse.json(validFundamentalsStockResponse)
      }),
    )
    renderView()

    await screen.findAllByTestId(/^watchlist-row-/)
    expect(fundamentalsRequestCount).toBe(0)

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /ver fundamentales de spy/i }))

    await screen.findByTestId('fundamentals-panel')
    expect(fundamentalsRequestCount).toBe(1)
  })
})

describe('sorting with nulls in the default sort column', () => {
  it('parks null combinedScore rows at the end of descending order, never interleaved with scored rows', async () => {
    // combinedScore is nullable now (Finding 1) — the default sort column
    // itself can carry nulls. Three scored rows plus one with every
    // value field null: a do-nothing sentinel (e.g. treating null as 0)
    // would place NOCOMB between MID (5) and LOW (2) instead of at the
    // true end of descending order.
    const withNullCombined = [
      { ticker: 'HIGH', price: '10.00', technicalScore: '8', wsbScore: '8', combinedScore: '8', signal: 'COMPRA' },
      { ticker: 'NOCOMB', price: null, technicalScore: null, wsbScore: null, combinedScore: null, signal: null },
      { ticker: 'MID', price: '20.00', technicalScore: '5', wsbScore: '5', combinedScore: '5', signal: 'MANTENER' },
      { ticker: 'LOW', price: '30.00', technicalScore: '2', wsbScore: '2', combinedScore: '2', signal: 'VENTA' },
    ]
    mockWatchlist(withNullCombined)
    renderView()

    const rows = await screen.findAllByTestId(/^watchlist-row-/)
    const tickerOrder = rows.map((row) => row.getAttribute('data-testid'))
    // Default sort is combinedScore descending: HIGH(8), MID(5), LOW(2),
    // then the null row at the stable end — never between two real scores.
    expect(tickerOrder).toEqual([
      'watchlist-row-HIGH',
      'watchlist-row-MID',
      'watchlist-row-LOW',
      'watchlist-row-NOCOMB',
    ])
  })

  it('renders a dash, never a BUY/SELL-implying badge, for a null signal', async () => {
    const withNullSignal = [
      { ticker: 'NOCOMB', price: null, technicalScore: null, wsbScore: null, combinedScore: null, signal: null },
    ]
    mockWatchlist(withNullSignal)
    renderView()

    const row = await screen.findByTestId('watchlist-row-NOCOMB')
    // Every nullable cell in this row (price, technical, WSB, combined,
    // signal) renders the same dash — none of them render as a Tag/badge.
    expect(within(row).getAllByText('—').length).toBeGreaterThanOrEqual(5)
    expect(within(row).queryByText('COMPRA')).not.toBeInTheDocument()
    expect(within(row).queryByText('VENTA')).not.toBeInTheDocument()
  })
})

describe('shared states', () => {
  it('shows the empty state when the watchlist has no items', async () => {
    mockWatchlist([])
    renderView()

    expect(await screen.findByText(/no hay tickers en la watchlist/i)).toBeInTheDocument()
  })

  it('shows the error state on a server failure', async () => {
    server.use(http.get(`${API_BASE}/watchlist`, () => HttpResponse.json({ error: 'boom' }, { status: 500 })))
    renderView()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
