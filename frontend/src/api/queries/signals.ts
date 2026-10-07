import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../client'
import {
  SignalHistoryResponseSchema,
  SignalRefreshResponseSchema,
  SignalsDashboardResponseSchema,
} from '../schemas/signals'

export const signalsKeys = {
  all: ['signals'] as const,
  history: (ticker: string, from: string, to: string) => ['signals', 'history', ticker, from, to] as const,
}

export function useSignalsDashboard() {
  return useQuery({
    queryKey: signalsKeys.all,
    queryFn: () => apiFetch('/signals', SignalsDashboardResponseSchema),
  })
}

export function useSignalHistory(ticker: string, from: string, to: string) {
  return useQuery({
    queryKey: signalsKeys.history(ticker, from, to),
    queryFn: () =>
      apiFetch(
        `/signals/history?ticker=${encodeURIComponent(ticker)}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
        SignalHistoryResponseSchema,
      ),
    enabled: ticker.length > 0 && from.length > 0 && to.length > 0,
  })
}

/**
 * Triggers an out-of-schedule signal refresh. Only `signalsKeys.all` is
 * invalidated on success — a refresh changes signals and nothing else, so
 * portfolio, returns and diversification (which derive from the
 * transaction history, not from signals) must not be refetched here.
 */
export function useRefreshSignals() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiFetch('/signals/refresh', SignalRefreshResponseSchema, { method: 'POST' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: signalsKeys.all })
    },
  })
}
