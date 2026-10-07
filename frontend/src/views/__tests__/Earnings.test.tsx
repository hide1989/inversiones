import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/msw/server'
import { earningsNotInProximityOrder } from '../../test/msw/fixtures'
import { Earnings } from '../Earnings'

const API_BASE = 'http://localhost:8080/api/v1'

function mockEarnings(body: ReadonlyArray<Record<string, unknown>>) {
  server.use(http.get(`${API_BASE}/earnings`, () => HttpResponse.json(body)))
}

function renderView() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <Earnings />
    </QueryClientProvider>,
  )
}

describe('the real live state: GET /earnings returns []', () => {
  it('shows the empty state', async () => {
    mockEarnings([])
    renderView()

    expect(await screen.findByText(/no hay reportes de resultados próximos/i)).toBeInTheDocument()
  })
})

describe('proximity ordering', () => {
  it('renders rows ordered by days remaining, ascending, even though the API body is not in that order', async () => {
    // earningsNotInProximityOrder is deliberately shuffled (MSFT 30 days,
    // AAPL 5 days, NVDA 13 days) — this only passes if the view actually
    // sorts by daysRemaining, not because the fixture already arrived in
    // proximity order.
    mockEarnings(earningsNotInProximityOrder)
    renderView()

    const rows = await screen.findAllByTestId(/^earnings-row-/)
    const tickerOrder = rows.map((row) => row.getAttribute('data-testid'))
    expect(tickerOrder).toEqual(['earnings-row-AAPL', 'earnings-row-NVDA', 'earnings-row-MSFT'])

    const daysHeader = screen.getByRole('columnheader', { name: /días restantes/i })
    expect(daysHeader).toHaveAttribute('aria-sort', 'ascending')
  })

  it('renders the days remaining, estimated EPS and historical surprise for each row', async () => {
    mockEarnings(earningsNotInProximityOrder)
    renderView()

    const aaplRow = await screen.findByTestId('earnings-row-AAPL')
    expect(within(aaplRow).getByText('5')).toBeInTheDocument()
    expect(within(aaplRow).getByText('1.85')).toBeInTheDocument()
    expect(within(aaplRow).getByText('-1.10')).toBeInTheDocument()
  })
})
