import { z } from 'zod'

// Mirrors WatchlistItemDto, returned as an array by GET /watchlist. The
// DTO's Javadoc documents that `price` and `technicalScore` are
// frequently absent upstream — confirmed live: 12 of 31 items return null
// for both. `wsbScore`, `combinedScore` and `signal` were present on
// every live item, so they stay required.
export const WatchlistItemSchema = z.object({
  ticker: z.string(),
  price: z.string().nullable(),
  technicalScore: z.string().nullable(),
  wsbScore: z.string(),
  combinedScore: z.string(),
  signal: z.string(),
})

export type WatchlistItem = z.infer<typeof WatchlistItemSchema>

export const WatchlistResponseSchema = z.array(WatchlistItemSchema)
