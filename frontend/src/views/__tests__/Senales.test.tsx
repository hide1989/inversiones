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

// A deliberately shuffled copy of the scored fixture's signals — same
// rows, same scores, just not in combined-score order. Asserting
// descending order against this proves the view sorts; asserting it
// against the fixture's own already-descending order would pass even
// against an implementation that does no sorting at all.
function shuffledScoredSignals() {
  const byTicker = new Map(validSignalsScoredResponse.signals.map((signal) => [signal.ticker, signal]))
  const order = ['GLD', 'TSLA', 'QQQ', 'GOOGL', 'MSFT', 'AMZN']
  return order.map((ticker) => {
    const signal = byTicker.get(ticker)
    if (signal === undefined) throw new Error(`fixture missing ${ticker}`)
    return signal
  })
}

// A fixture whose TECHNICAL ranking deliberately disagrees with its
// combined-score ranking, so clicking the TECHNICAL header produces a
// visibly different row order than the default — proving the column
// comparator actually re-sorts, not just that the initial API order
// happens to agree with it (which the scored fixture above would not
// have shown: its TECHNICAL values already rank the same as its
// combined scores).
const signalsWithDivergentTechnicalRanking = {
  ...validSignalsScoredResponse,
  signals: [
    { ticker: 'QQQ', snapshot: { ticker: 'QQQ', snapshotDate: '2026-10-06', scores: { TECHNICAL: '3.0' } }, score: { value: '8.40', level: 'STRONG_BUY', usedSources: ['TECHNICAL'] } },
    { ticker: 'MSFT', snapshot: { ticker: 'MSFT', snapshotDate: '2026-10-06', scores: { TECHNICAL: '9.5' } }, score: { value: '7.20', level: 'BUY', usedSources: ['TECHNICAL'] } },
    { ticker: 'AMZN', snapshot: { ticker: 'AMZN', snapshotDate: '2026-10-06', scores: { TECHNICAL: '8.0' } }, score: { value: '6.10', level: 'BUY', usedSources: ['TECHNICAL'] } },
    { ticker: 'TSLA', snapshot: { ticker: 'TSLA', snapshotDate: '2026-10-06', scores: { TECHNICAL: '7.0' } }, score: { value: '4.80', level: 'HOLD', usedSources: ['TECHNICAL'] } },
    { ticker: 'GOOGL', snapshot: { ticker: 'GOOGL', snapshotDate: '2026-10-06', scores: { TECHNICAL: '6.0' } }, score: { value: '3.10', level: 'SELL', usedSources: ['TECHNICAL'] } },
    { ticker: 'GLD', snapshot: { ticker: 'GLD', snapshotDate: '1970-01-01', scores: {} }, score: { value: '0.00', level: 'STRONG_SELL', usedSources: [] } },
  ],
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
    // The API body itself is shuffled (see shuffledScoredSignals), so this
    // only passes if the view actually sorts by combined score — not
    // because the fixture already arrived in that order.
    mockSignals({ ...validSignalsScoredResponse, signals: shuffledScoredSignals() })
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

    // The default sort is a real instruction, not an accident that merely
    // holds because the API happened to pre-sort: the Score header must
    // report its own state via aria-sort from the very first render.
    const scoreHeader = screen.getByRole('columnheader', { name: /score/i })
    expect(scoreHeader).toHaveAttribute('aria-sort', 'descending')
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
    // TECHNICAL's ranking here deliberately disagrees with the combined
    // score's ranking (see signalsWithDivergentTechnicalRanking), so the
    // row order after clicking the header is visibly different from the
    // default — proving the click re-sorts by TECHNICAL rather than the
    // rows merely staying in whatever order they already had.
    mockSignals(signalsWithDivergentTechnicalRanking)
    mockHistory([])
    renderView()

    await screen.findAllByTestId(/^signal-row-/)
    const user = userEvent.setup()
    const technicalHeader = screen.getByRole('button', { name: /technical/i })
    await user.click(technicalHeader)

    const rows = await screen.findAllByTestId(/^signal-row-/)
    const tickerOrder = rows.map((row) => row.getAttribute('data-testid'))
    // Descending by TECHNICAL: MSFT 9.5, AMZN 8.0, TSLA 7.0, GOOGL 6.0, QQQ 3.0, GLD has none.
    expect(tickerOrder).toEqual([
      'signal-row-MSFT',
      'signal-row-AMZN',
      'signal-row-TSLA',
      'signal-row-GOOGL',
      'signal-row-QQQ',
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
