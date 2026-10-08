import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/msw/server'
import {
  validAiSynthesisEmptyResponse,
  validAiSynthesisWithDataResponse,
  validAiSynthesisWithNullFieldsResponse,
} from '../../test/msw/fixtures'
import { SintesisIA } from '../SintesisIA'

const API_BASE = 'http://localhost:8080/api/v1'

function mockAiSynthesis(body: Record<string, unknown>) {
  server.use(http.get(`${API_BASE}/ai-synthesis`, () => HttpResponse.json(body)))
}

function renderView() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SintesisIA />
    </QueryClientProvider>,
  )
}

describe('the real live-captured no-data state (withData: [], averageScore: "0.00")', () => {
  it('renders no zero figure anywhere and states that no average can be computed', async () => {
    mockAiSynthesis(validAiSynthesisEmptyResponse)
    renderView()

    await screen.findByText(/ningún ticker tiene síntesis ia/i)

    // The whole point: a "0.00" computed over zero scored tickers must
    // never reach the DOM looking like a measured score of zero.
    const bareZeroPattern = /(?<![.\d])0(?![.\d])/
    expect(document.body.textContent ?? '').not.toMatch(bareZeroPattern)
    expect(screen.queryByText('0.00')).not.toBeInTheDocument()
  })

  it('lists every withoutData ticker explicitly', async () => {
    mockAiSynthesis(validAiSynthesisEmptyResponse)
    renderView()

    const note = await screen.findByRole('note')
    for (const ticker of validAiSynthesisEmptyResponse.withoutData) {
      expect(within(note).getByText(new RegExp(ticker))).toBeInTheDocument()
    }
  })
})

describe('a response with some scored tickers', () => {
  it('renders a card per withData ticker with score, signal, conviction, horizon, summary, catalysts and risks', async () => {
    mockAiSynthesis(validAiSynthesisWithDataResponse)
    renderView()

    const card = await screen.findByTestId('synthesis-card-AAPL')
    expect(within(card).getByText('8.2')).toBeInTheDocument()
    expect(within(card).getByText('BUY')).toBeInTheDocument()
    expect(within(card).getByText(/HIGH/)).toBeInTheDocument()
    expect(within(card).getByText(/MEDIUM_TERM/)).toBeInTheDocument()
    expect(within(card).getByText(/impulso en servicios/)).toBeInTheDocument()
    expect(within(card).getByText(/Lanzamiento de nuevo hardware/)).toBeInTheDocument()
    expect(within(card).getByText(/Desaceleración en China/)).toBeInTheDocument()
  })

  it('labels the average as computed only over the scored tickers, listing the excluded ones', async () => {
    mockAiSynthesis(validAiSynthesisWithDataResponse)
    renderView()

    await screen.findByTestId('synthesis-card-AAPL')
    expect(screen.getByText('7.8')).toBeInTheDocument()
    expect(screen.getByText(/2 tickers con datos/i)).toBeInTheDocument()
    expect(screen.getByText(/excluye 3 tickers sin datos: amzn, gld, nu/i)).toBeInTheDocument()
  })
})

describe('a null iaScore, signal, conviction, horizon or summary (Critical 2: all five are unguarded on the wire)', () => {
  it('renders every card instead of ErrorState, with a no-data treatment per null field', async () => {
    mockAiSynthesis(validAiSynthesisWithNullFieldsResponse)
    renderView()

    const aaplCard = await screen.findByTestId('synthesis-card-AAPL')
    const msftCard = await screen.findByTestId('synthesis-card-MSFT')

    // AAPL: null iaScore and conviction, real signal/horizon/summary.
    expect(within(aaplCard).getByText('Sin dato')).toBeInTheDocument()
    expect(within(aaplCard).getByText('BUY')).toBeInTheDocument()
    expect(within(aaplCard).getByText(/Convicción: Sin dato/)).toBeInTheDocument()
    expect(within(aaplCard).getByText(/Horizonte: MEDIUM_TERM/)).toBeInTheDocument()

    // MSFT: null signal, horizon and summary, real iaScore/conviction.
    expect(within(msftCard).getByText('7.4')).toBeInTheDocument()
    expect(within(msftCard).getByText(/Convicción: MEDIUM/)).toBeInTheDocument()
    expect(within(msftCard).getByText(/Horizonte: Sin dato/)).toBeInTheDocument()
    expect(within(msftCard).getByText(/Sin resumen disponible/)).toBeInTheDocument()

    expect(document.body.textContent ?? '').not.toContain('null')
  })
})
