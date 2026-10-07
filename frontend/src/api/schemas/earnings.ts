import { z } from 'zod'

// Mirrors EarningsEventDto, returned as an array by GET /earnings.
export const EarningsEventSchema = z.object({
  ticker: z.string(),
  reportDate: z.string(),
  daysRemaining: z.number(),
  estimatedEps: z.string(),
  historicalSurprise: z.string(),
})

export type EarningsEvent = z.infer<typeof EarningsEventSchema>

export const EarningsResponseSchema = z.array(EarningsEventSchema)
