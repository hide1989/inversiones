import { z } from 'zod'

// Mirrors WatchlistItemDto, returned as an array by GET /watchlist.
export const WatchlistItemSchema = z.object({
  ticker: z.string(),
  price: z.string(),
  technicalScore: z.string(),
  wsbScore: z.string(),
  combinedScore: z.string(),
  signal: z.string(),
})

export type WatchlistItem = z.infer<typeof WatchlistItemSchema>

export const WatchlistResponseSchema = z.array(WatchlistItemSchema)
