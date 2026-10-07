import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { TotalReturnResponseSchema } from '../schemas/returns'

export const returnsKeys = {
  all: ['portfolio', 'returns'] as const,
}

export function useTotalReturn() {
  return useQuery({
    queryKey: returnsKeys.all,
    queryFn: () => apiFetch('/portfolio/returns', TotalReturnResponseSchema),
  })
}
