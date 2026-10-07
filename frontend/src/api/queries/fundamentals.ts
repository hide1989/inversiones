import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { FundamentalsSchema } from '../schemas/fundamentals'

export const fundamentalsKeys = {
  detail: (ticker: string) => ['fundamentals', ticker] as const,
}

export function useFundamentals(ticker: string) {
  return useQuery({
    queryKey: fundamentalsKeys.detail(ticker),
    queryFn: () => apiFetch(`/fundamentals/${encodeURIComponent(ticker)}`, FundamentalsSchema),
    enabled: ticker.length > 0,
  })
}
