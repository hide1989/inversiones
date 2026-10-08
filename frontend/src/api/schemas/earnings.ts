import { z } from 'zod'

// Mirrors EarningsEventDto, returned as an array by GET /earnings.
export const EarningsEventSchema = z.object({
  ticker: z.string(),
  reportDate: z.iso.date(),
  daysRemaining: z.number(),
  estimatedEps: z.string().nullable(),
  historicalSurprise: z.string().nullable(),
})

export type EarningsEvent = z.infer<typeof EarningsEventSchema>

export const EarningsResponseSchema = z.array(EarningsEventSchema)
