import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiFetch, setAuthToken } from '../client'
import { ApiError, UnauthorizedError } from '../problem'
import { usePortfolio } from '../queries/portfolio'
import { PortfolioResponseSchema } from '../schemas/portfolio'
import { TotalReturnResponseSchema } from '../schemas/returns'
import { server } from '../../test/msw/server'
import { totalReturnUnavailable, validPortfolioResponse } from '../../test/msw/fixtures'

const API_BASE = 'http://localhost:8080/api/v1'

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}

beforeEach(() => {
  setAuthToken(null)
})

describe('a valid PortfolioResponse', () => {
  it('parses, and usePortfolio returns typed data', async () => {
    const { result } = renderHook(() => usePortfolio(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data?.baseCurrency).toBe('COP')
    expect(result.current.data?.totalInvested.amount).toBe('2463.0000000000')
    expect(result.current.data?.positions[0]?.ticker).toBe('QQQ')
  })
})

describe('a response missing a required field', () => {
  it('throws, and the message names the missing field', async () => {
    server.use(
      http.get(`${API_BASE}/portfolio`, () => {
        const withoutBaseCurrency: Record<string, unknown> = { ...validPortfolioResponse }
        delete withoutBaseCurrency.baseCurrency
        return HttpResponse.json(withoutBaseCurrency)
      }),
    )

    await expect(apiFetch('/portfolio', PortfolioResponseSchema)).rejects.toThrow(/baseCurrency/)
  })
})

describe('a response where a Money amount is a JSON number instead of a string', () => {
  it('throws rather than silently coercing', async () => {
    server.use(
      http.get(`${API_BASE}/portfolio`, () =>
        HttpResponse.json({
          ...validPortfolioResponse,
          totalInvested: { amount: 2463.0, currency: 'USD' },
        }),
      ),
    )

    await expect(apiFetch('/portfolio', PortfolioResponseSchema)).rejects.toThrow(/totalInvested/)
  })
})

describe('a problem+json 400 response', () => {
  it('becomes a typed ApiError with detail preserved', async () => {
    server.use(
      http.get(`${API_BASE}/portfolio`, () =>
        HttpResponse.json(
          {
            type: 'https://api.inversiones.local/errors/validation',
            title: 'Bad Request',
            status: 400,
            detail: 'quantity must be positive',
            instance: '/api/v1/portfolio',
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    let caught: unknown
    try {
      await apiFetch('/portfolio', PortfolioResponseSchema)
    } catch (error) {
      caught = error
    }

    expect(caught).toBeInstanceOf(ApiError)
    expect((caught as ApiError).problem.detail).toBe('quantity must be positive')
    expect((caught as ApiError).problem.status).toBe(400)
  })
})

describe('a 401 response', () => {
  it('throws the distinguishable UnauthorizedError type', async () => {
    server.use(
      http.get(`${API_BASE}/portfolio`, () =>
        HttpResponse.json(
          {
            type: 'https://api.inversiones.local/errors/unauthorized',
            title: 'Unauthorized',
            status: 401,
            detail: 'missing or invalid token',
            instance: '/api/v1/portfolio',
          },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    let caught: unknown
    try {
      await apiFetch('/portfolio', PortfolioResponseSchema)
    } catch (error) {
      caught = error
    }

    expect(caught).toBeInstanceOf(UnauthorizedError)
    // Must be distinguishable BY TYPE, not merely by reading .problem.status.
    expect(caught instanceof UnauthorizedError).toBe(true)
  })
})

describe('TotalReturnResponse with the currency effect unavailable', () => {
  it('parses successfully rather than being rejected as malformed', async () => {
    server.use(http.get(`${API_BASE}/portfolio/returns`, () => HttpResponse.json(totalReturnUnavailable)))

    const result = await apiFetch('/portfolio/returns', TotalReturnResponseSchema)

    expect(result.currencyEffectAvailable).toBe(false)
    expect(result.currentRate).toBeNull()
    expect(result.exchangeEffect).toBeNull()
    expect(result.assetGainInCop).toBeNull()
    expect(result.totalReturnInCop).toBeNull()
  })
})
