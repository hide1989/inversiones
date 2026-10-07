import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { PortfolioResponseSchema } from '../schemas/portfolio'

export const portfolioKeys = {
  all: ['portfolio'] as const,
}

export function usePortfolio() {
  return useQuery({
    queryKey: portfolioKeys.all,
    queryFn: () => apiFetch('/portfolio', PortfolioResponseSchema),
  })
}
