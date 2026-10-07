import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { DiversificationReportSchema } from '../schemas/diversification'

export const diversificationKeys = {
  all: ['diversification'] as const,
}

export function useDiversification() {
  return useQuery({
    queryKey: diversificationKeys.all,
    queryFn: () => apiFetch('/diversification', DiversificationReportSchema),
  })
}
