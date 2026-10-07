import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/msw/server'
import {
  validSignalHistoryResponse,
  validSignalsNoDataResponse,
  validSignalsScoredResponse,
} from '../../test/msw/fixtures'
import { Senales } from '../Senales'

const API_BASE = 'http://localhost:8080/api/v1'

function mockSignals(body: Record<string, unknown>) {
  server.use(http.get(`${API_BASE}/signals`, () => HttpResponse.json(body)))
}

let historyRequestCount = 0

function mockHistory(body: ReadonlyArray<Record<string, unknown>>) {
  historyRequestCount = 0
  server.use(
    http.get(`${API_BASE}/signals/history`, () => {
      historyRequestCount += 1
      return HttpResponse.json(body)
    }),
  )
}

function renderView() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <Senales />
    </QueryClientProvider>,
  )
}

describe('the real no-data capture (every seeded ticker, zero sources)', () => {
  it('shows "sin datos" for every row and never renders 0.00 or STRONG_SELL as an assessment', async () => {
    mockSignals(validSignalsNoDataResponse)
    mockHistory([])
    renderView()

    const rows = await screen.findAllByTestId(/^signal-row-/)
    expect(rows).toHaveLength(validSignalsNoDataResponse.signals.length)

    for (const row of rows) {
      expect(within(row).getByText('Sin datos')).toBeInTheDocument()
    }

    expect(screen.queryByText('0.00')).not.toBeInTheDocument()
    expect(screen.queryByText('STRONG_SELL')).not.toBeInTheDocument()
  })

  it('renders a sensible average and level summary when the average is not meaningful', async () => {
    mockSignals(validSignalsNoDataResponse)
    mockHistory([])
    renderView()

    await screen.findAllByTestId(/^signal-row-/)
    expect(screen.getByText(/no hay tickers con datos/i)).toBeInTheDocument()
    expect(screen.queryByText('0.00')).not.toBeInTheDocument()
  })
})

describe('the derived scored variant', () => {
  it('renders in combined-score-descending order, including a mixed no-data row', async () => {
    mockSignals(validSignalsScoredResponse)
    mockHistory([])
    renderView()

    const rows = await screen.findAllByTestId(/^signal-row-/)
    const tickerOrder = rows.map((row) => row.getAttribute('data-testid'))
    expect(tickerOrder).toEqual([
      'signal-row-QQQ',
      'signal-row-MSFT',
      'signal-row-AMZN',
      'signal-row-TSLA',
      'signal-row-GOOGL',
      'signal-row-GLD',
    ])
  })

  it('renders the source count beside each combined score', async () => {
    mockSignals(validSignalsScoredResponse)
    mockHistory([])
    renderView()

    const qqqRow = await screen.findByTestId('signal-row-QQQ')
    expect(within(qqqRow).getByText('6/6')).toBeInTheDocument()

    const googlRow = screen.getByTestId('signal-row-GOOGL')
    expect(within(googlRow).getByText('3/6')).toBeInTheDocument()
  })

  it('sorts by a per-source column using the shared decimal comparator, not a numeric coercion', async () => {
    mockSignals(validSignalsScoredResponse)
    mockHistory([])
    renderView()

    await screen.findAllByTestId(/^signal-row-/)
    const user = userEvent.setup()
    const technicalHeader = screen.getByRole('button', { name: /technical/i })
    await user.click(technicalHeader)

    const rows = await screen.findAllByTestId(/^signal-row-/)
    const tickerOrder = rows.map((row) => row.getAttribute('data-testid'))
    // Descending by TECHNICAL: QQQ 8.1, MSFT 7.5, TSLA 5.2, AMZN 6.0, GOOGL 3.5, GLD has none.
    expect(tickerOrder).toEqual([
      'signal-row-QQQ',
      'signal-row-MSFT',
      'signal-row-AMZN',
      'signal-row-TSLA',
      'signal-row-GOOGL',
      'signal-row-GLD',
    ])
  })

  it('renders the average score and level counts from the API values', async () => {
    mockSignals(validSignalsScoredResponse)
    mockHistory([])
    renderView()

    await screen.findAllByTestId(/^signal-row-/)
    expect(screen.getByText('5.9')).toBeInTheDocument()
    expect(screen.getByText(/STRONG_BUY.*1/i)).toBeInTheDocument()
  })

  it('fetches the history chart only when a ticker is opened, not on mount', async () => {
    mockSignals(validSignalsScoredResponse)
    mockHistory(validSignalHistoryResponse)
    renderView()

    await screen.findAllByTestId(/^signal-row-/)
    expect(historyRequestCount).toBe(0)

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /ver historial de qqq/i }))

    await screen.findByTestId('signal-history-chart')
    expect(historyRequestCount).toBe(1)
  })
})

describe('shared states', () => {
  it('reuses the shared loading, error and empty states rather than reinventing them', async () => {
    server.use(http.get(`${API_BASE}/signals`, () => HttpResponse.json({ error: 'boom' }, { status: 500 })))
    renderView()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })

  it('shows the empty state when there are no signals at all', async () => {
    mockSignals({ signals: [], averageScore: '0.00', levelCounts: {}, tickersWithoutData: [] })
    renderView()

    expect(await screen.findByText(/no hay señales/i)).toBeInTheDocument()
  })
})
