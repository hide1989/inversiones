import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider } from '../../auth/AuthProvider'
import { server } from '../../test/msw/server'
import { Posiciones } from '../Posiciones'

const API_BASE = 'http://localhost:8080/api/v1'

// The real seeded portfolio, captured straight from the running backend
// (20 instruments, 26 positions across Hapi/ARQ/Trii, USD and COP) rather
// than hand-written, so these assertions exercise the actual contract
// shape — see .superpowers/sdd/2026-10-06-frontend-spa/task-6-report.md.
const fixturePath = resolve(dirname(fileURLToPath(import.meta.url)), '../../test/msw/fixtures/portfolio.json')

function asRecord(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null) {
    throw new Error('expected an object fixture')
  }
  return value as Record<string, unknown>
}

const realPortfolio: Record<string, unknown> = asRecord(JSON.parse(readFileSync(fixturePath, 'utf-8')))

function clonePortfolio(): Record<string, unknown> {
  return asRecord(JSON.parse(JSON.stringify(realPortfolio)))
}

function mockPortfolio(body: Record<string, unknown>, init?: { status?: number }) {
  server.use(
    http.get(`${API_BASE}/portfolio`, () => {
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

function renderPosiciones(initialEntries: string[] = ['/posiciones']) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <AuthProvider>
        <Routes>
          <Route path="/posiciones" element={<Posiciones />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('the real seeded portfolio', () => {
  it('renders actual tickers and amounts from the captured fixture', async () => {
    mockPortfolio(realPortfolio)
    renderPosiciones()

    // A USD position (Hapi) and a COP position (Trii/BVC) from the capture.
    expect(await screen.findByTestId('position-row-QQQ-Hapi')).toBeInTheDocument()
    const terpelRow = screen.getByTestId('position-row-TERPEL-Trii')
    expect(within(terpelRow).getByText('482,500.00')).toBeInTheDocument()

    const fixture = asRecord(realPortfolio)
    const totalInvested = asRecord(fixture.totalInvested)
    expect(totalInvested.amount).toBe('3816.4299480011')
    // formatMoney renders the API's own total, never a browser-side sum.
    expect(screen.getByText('USD 3,816.43')).toBeInTheDocument()
  })

  it('renders the base currency explicitly rather than assuming it', async () => {
    mockPortfolio(realPortfolio)
    renderPosiciones()

    await screen.findByTestId('position-row-QQQ-Hapi')
    expect(screen.getByText('Divisa base')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
  })

  it('shows a peso position in its own native currency while the totals stay in the base currency', async () => {
    mockPortfolio(realPortfolio)
    renderPosiciones()

    const terpelRow = await screen.findByTestId('position-row-TERPEL-Trii')
    // Native: pesos, straight from marketValue (COP).
    expect(within(terpelRow).getByText('482,500.00')).toBeInTheDocument()
    // Base-currency equivalent annotation, from marketValueInBaseCurrency (USD) — not computed.
    expect(within(terpelRow).getByText(/USD 150\.80/)).toBeInTheDocument()
    // The summary total is in the base currency (USD), unaffected by any peso row.
    expect(screen.getByText('USD 4,378.80')).toBeInTheDocument()
  })
})

describe('a position with priced: false', () => {
  it('shows "sin valorar" and names both possible causes', async () => {
    const withUnpriced = clonePortfolio()
    const positions = withUnpriced.positions as Array<Record<string, unknown>>
    const firstPosition = positions[0]
    if (firstPosition === undefined) throw new Error('fixture has no positions')
    firstPosition.priced = false
    withUnpriced.unpricedTickers = [firstPosition.ticker]
    mockPortfolio(withUnpriced)

    renderPosiciones()
    // Scoped to the market-value cell: the three other priced:false cells
    // in this row (Día %, P&L, P&L %) now also carry their own accessible
    // "Sin valorar" text (an sr-only span, per NoDataCell), which is the
    // Important 3 fix — a screen reader must not hear a silently empty
    // cell. getByText across the whole row would therefore match four
    // times; the visible Tag + explanation is specifically the
    // marketValue cell's non-compact rendering.
    const row = await screen.findByTestId(`position-row-${firstPosition.ticker}-${firstPosition.brokerName}`)
    const marketValueCell = within(row).getAllByText('Sin valorar')[0]
    expect(marketValueCell).toBeInTheDocument()
    expect(within(row).getByText(/falta precio de mercado o tasa de cambio/i)).toBeInTheDocument()
  })

  it('drives a visible summary note from unpricedTickers', async () => {
    const withUnpriced = clonePortfolio()
    const positions = withUnpriced.positions as Array<Record<string, unknown>>
    const firstPosition = positions[0]
    if (firstPosition === undefined) throw new Error('fixture has no positions')
    firstPosition.priced = false
    withUnpriced.unpricedTickers = [firstPosition.ticker]
    mockPortfolio(withUnpriced)

    renderPosiciones()
    const note = await screen.findByRole('note')
    expect(note).toHaveTextContent(String(firstPosition.ticker))
  })
})

describe('sorting', () => {
  it('reorders rows by unrealized P&L and updates the URL; reloading with that URL preserves the order', async () => {
    mockPortfolio(realPortfolio)
    const user = userEvent.setup()
    const firstRender = renderPosiciones()

    await screen.findByTestId('position-row-QQQ-Hapi')
    const [header] = screen.getAllByRole('button', { name: /p&l no realizado/i })
    if (header === undefined) throw new Error('expected a sortable P&L header')
    await user.click(header)

    // EC/Trii has the largest USD unrealizedPnl (257.34) in the capture.
    const rows = screen.getAllByTestId(/^position-row-/)
    expect(rows[0]).toHaveAttribute('data-testid', 'position-row-EC-Trii')
    firstRender.unmount()

    // Reload with the URL the click produced.
    const { unmount } = renderPosiciones(['/posiciones?sort=unrealizedPnl&dir=desc'])
    await screen.findByTestId('position-row-EC-Trii')
    const reloadedRows = screen.getAllByTestId(/^position-row-/)
    expect(reloadedRows[0]).toHaveAttribute('data-testid', 'position-row-EC-Trii')
    unmount()
  })
})

describe('loading, error and empty states', () => {
  it('shows skeletons while loading', () => {
    mockPortfolio(realPortfolio)
    renderPosiciones()
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('shows the error state with the server message and a retry affordance', async () => {
    mockPortfolio(
      { type: 'about:blank', title: 'Error', status: 500, detail: 'El servidor falló al calcular el portafolio.', instance: '/api/v1/portfolio' },
      { status: 500 },
    )
    renderPosiciones()

    expect(await screen.findByText('El servidor falló al calcular el portafolio.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reintentar/i })).toBeInTheDocument()
  })

  it('shows the empty state for an empty portfolio', async () => {
    mockPortfolio({
      positions: [],
      totalInvested: { amount: '0.0000000000', currency: 'USD' },
      totalMarketValue: { amount: '0.0000000000', currency: 'USD' },
      totalUnrealizedPnl: { amount: '0.0000000000', currency: 'USD' },
      unpricedTickers: [],
      baseCurrency: 'USD',
    })
    renderPosiciones()

    expect(await screen.findByText(/no tienes posiciones registradas/i)).toBeInTheDocument()
  })
})
