import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { WatchlistResponseSchema } from '../schemas/watchlist'

export const watchlistKeys = {
  all: ['watchlist'] as const,
}

export function useWatchlist() {
  return useQuery({
    queryKey: watchlistKeys.all,
    queryFn: () => apiFetch('/watchlist', WatchlistResponseSchema),
  })
}
