import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider } from '../../auth/AuthProvider'
import { server } from '../../test/msw/server'
import { validDiversificationResponse } from '../../test/msw/fixtures'
import { Diversificacion } from '../Diversificacion'

const API_BASE = 'http://localhost:8080/api/v1'

function clone(): Record<string, unknown> {
  return JSON.parse(JSON.stringify(validDiversificationResponse)) as Record<string, unknown>
}

function mockDiversification(body: Record<string, unknown>, init?: { status?: number }) {
  server.use(
    http.get(`${API_BASE}/diversification`, () => {
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

function renderDiversificacion() {
  return render(
    <MemoryRouter initialEntries={['/diversificacion']}>
      <AuthProvider>
        <Routes>
          <Route path="/diversificacion" element={<Diversificacion />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('a compliant report (the real captured, zero-violation backend state)', () => {
  it('renders the compliant state and no violations', async () => {
    mockDiversification(validDiversificationResponse)
    renderDiversificacion()

    await screen.findByText('TERPEL')
    expect(screen.getByText(/cumple todos los límites/i)).toBeInTheDocument()
    expect(screen.queryByText(/excede el límite/i)).not.toBeInTheDocument()
  })

  it('renders ticker and sector weights straight from the API strings, with no re-computation', async () => {
    mockDiversification(validDiversificationResponse)
    renderDiversificacion()

    // TERPEL: "3.4417" -> formatPercent's default 2-decimal rendering, "3.44%".
    const terpelRow = await screen.findByTestId('weight-row-TERPEL')
    expect(within(terpelRow).getByText('3.44%')).toBeInTheDocument()

    // Energy sector: "23.6432" -> "23.64%".
    const energyRow = screen.getByTestId('weight-row-Energy')
    expect(within(energyRow).getByText('23.64%')).toBeInTheDocument()
  })
})

describe('a report with a ticker violation and a sector violation', () => {
  it('renders both violations with their subjects and weights', async () => {
    const withViolations = clone()
    withViolations.compliant = false
    withViolations.violations = [
      { limit: 'SINGLE_STOCK', subject: 'EC', actualWeight: '21.5000', allowedWeight: '20.0000' },
      { limit: 'SECTOR', subject: 'Energy', actualWeight: '31.2000', allowedWeight: '30.0000' },
    ]
    mockDiversification(withViolations)
    renderDiversificacion()

    await screen.findByText('TERPEL')
    expect(screen.getByText(/no cumple los límites/i)).toBeInTheDocument()

    const tickerRow = screen.getByTestId('weight-row-EC')
    expect(within(tickerRow).getAllByText(/excede el límite/i).length).toBeGreaterThan(0)
    expect(within(tickerRow).getByText(/21\.50%/)).toBeInTheDocument()

    const sectorRow = screen.getByTestId('weight-row-Energy')
    expect(within(sectorRow).getAllByText(/excede el límite/i).length).toBeGreaterThan(0)
    expect(within(sectorRow).getByText(/31\.20%/)).toBeInTheDocument()
  })
})

describe('loading, error and empty states', () => {
  it('shows skeletons while loading', () => {
    mockDiversification(validDiversificationResponse)
    renderDiversificacion()
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('shows the error state with the server message and a retry affordance', async () => {
    mockDiversification(
      {
        type: 'about:blank',
        title: 'Error',
        status: 500,
        detail: 'El servidor falló al calcular la diversificación.',
        instance: '/api/v1/diversification',
      },
      { status: 500 },
    )
    renderDiversificacion()

    expect(await screen.findByText('El servidor falló al calcular la diversificación.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reintentar/i })).toBeInTheDocument()
  })

  it('shows the empty state for an empty portfolio', async () => {
    mockDiversification({
      violations: [],
      totalMarketValue: { amount: '0.0000000000', currency: 'USD' },
      weightByTicker: {},
      weightBySector: {},
      compliant: true,
    })
    renderDiversificacion()

    expect(await screen.findByText(/no hay posiciones/i)).toBeInTheDocument()
  })
})

describe('weight ordering', () => {
  it('lists tickers from largest to smallest weight, not in the API map insertion order', async () => {
    mockDiversification(validDiversificationResponse)
    renderDiversificacion()

    await screen.findByText('TERPEL')
    const tickerCells = screen.getAllByTestId(/^weight-row-/)
    const tickerIds = tickerCells.map((el) => el.getAttribute('data-testid'))
    // EC (19.1559) is the largest ticker weight in the fixture, which is
    // NOT first in weightByTicker's own key order (TERPEL is) — so this
    // assertion only passes if the view actually sorts by weight.
    const firstTickerIndex = tickerIds.findIndex((id) => id === 'weight-row-EC')
    const terpelIndex = tickerIds.findIndex((id) => id === 'weight-row-TERPEL')
    expect(firstTickerIndex).toBeGreaterThanOrEqual(0)
    expect(firstTickerIndex).toBeLessThan(terpelIndex)
  })
})
