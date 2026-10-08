import { z } from 'zod'

// Mirrors WatchlistItemDto, returned as an array by GET /watchlist. All
// five value fields are nullable, not just the two a sample happened to
// show null: `price`, `technicalScore`, `wsbScore` and `combinedScore`
// are all plain `BigDecimal` (nullable on the wire by the same
// DecimalStrings.of(BigDecimal) rule as fundamentals.ts), and `signal` is
// a plain `String` passed straight through the DTO with no null check.
// `price`/`technicalScore` are confirmed null live (12 of 31 items); the
// other three just hadn't been observed null in that one sample.
export const WatchlistItemSchema = z.object({
  ticker: z.string(),
  price: z.string().nullable(),
  technicalScore: z.string().nullable(),
  wsbScore: z.string().nullable(),
  combinedScore: z.string().nullable(),
  signal: z.string().nullable(),
})

export type WatchlistItem = z.infer<typeof WatchlistItemSchema>

export const WatchlistResponseSchema = z.array(WatchlistItemSchema)
