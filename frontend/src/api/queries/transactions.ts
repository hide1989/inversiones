import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  TransactionListResponseSchema,
  TransactionResponseSchema,
  type TransactionRequest,
} from '../schemas/transactions'
import { z } from 'zod'
import { portfolioKeys } from './portfolio'
import { returnsKeys } from './returns'
import { diversificationKeys } from './diversification'

/** The listing endpoint's optional filters, each an exact `TransactionController` query param. */
export interface TransactionFilters {
  ticker?: string
  brokerName?: string
  from?: string
  to?: string
}

export const transactionsKeys = {
  all: ['transactions'] as const,
  // The key MUST carry the filter values: with a bare `all` key, two different
  // filters would collide on one cache entry and TanStack Query would serve one
  // filter's result for the other's request.
  list: (filters: TransactionFilters) => [...transactionsKeys.all, filters] as const,
}

function buildQueryString(filters: TransactionFilters): string {
  const params = new URLSearchParams()
  if (filters.ticker) params.set('ticker', filters.ticker)
  if (filters.brokerName) params.set('brokerName', filters.brokerName)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  const query = params.toString()
  return query.length > 0 ? `?${query}` : ''
}

export function useTransactions(filters: TransactionFilters = {}) {
  return useQuery({
    queryKey: transactionsKeys.list(filters),
    queryFn: () => apiFetch(`/transactions${buildQueryString(filters)}`, TransactionListResponseSchema),
  })
}

/**
 * Every transaction mutation changes data that four endpoints derive from:
 * the transactions list itself, the positions/cost basis in `/portfolio`,
 * the total return, and the diversification report. Signals are NOT
 * invalidated here — they come from a scheduled snapshot job, not from
 * the transaction history, so a trade does not change them.
 *
 * `returnsKeys.all` (`['portfolio', 'returns']`) is a prefix-extension of
 * `portfolioKeys.all` (`['portfolio']`), so invalidating portfolio above
 * already cascades to returns through TanStack Query's prefix matching.
 * It is invalidated explicitly anyway: relying on the implicit cascade
 * means a later refactor of either key array silently stops refreshing
 * the total return panel, with no test failing to catch it.
 */
function invalidateAfterMutation(queryClient: QueryClient): void {
  void queryClient.invalidateQueries({ queryKey: transactionsKeys.all })
  void queryClient.invalidateQueries({ queryKey: portfolioKeys.all })
  void queryClient.invalidateQueries({ queryKey: returnsKeys.all })
  void queryClient.invalidateQueries({ queryKey: diversificationKeys.all })
}

export function useCreateTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: TransactionRequest) =>
      apiFetch('/transactions', TransactionResponseSchema, { method: 'POST', body }),
    onSuccess: () => invalidateAfterMutation(queryClient),
  })
}

export function useUpdateTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: TransactionRequest }) =>
      apiFetch(`/transactions/${id}`, TransactionResponseSchema, { method: 'PUT', body }),
    onSuccess: () => invalidateAfterMutation(queryClient),
  })
}

export function useDeleteTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiFetch(`/transactions/${id}`, z.void(), { method: 'DELETE' }),
    onSuccess: () => invalidateAfterMutation(queryClient),
  })
}
