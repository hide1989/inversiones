import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { SignalHistoryResponseSchema, SignalsDashboardResponseSchema } from '../schemas/signals'

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
