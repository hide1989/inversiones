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
