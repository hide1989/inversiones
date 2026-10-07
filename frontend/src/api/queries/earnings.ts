import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { EarningsResponseSchema } from '../schemas/earnings'

export const earningsKeys = {
  all: ['earnings'] as const,
}

export function useEarnings() {
  return useQuery({
    queryKey: earningsKeys.all,
    queryFn: () => apiFetch('/earnings', EarningsResponseSchema),
  })
}
