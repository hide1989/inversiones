import { z } from 'zod'
import { MoneySchema } from './common'

// Mirrors DiversificationViolationDto.
export const DiversificationViolationSchema = z.object({
  limit: z.string(),
  subject: z.string(),
  actualWeight: z.string(),
  allowedWeight: z.string(),
})

export type DiversificationViolation = z.infer<typeof DiversificationViolationSchema>

// Mirrors DiversificationReportDto, returned by GET /diversification.
export const DiversificationReportSchema = z.object({
  violations: z.array(DiversificationViolationSchema),
  totalMarketValue: MoneySchema,
  weightByTicker: z.record(z.string(), z.string()),
  weightBySector: z.record(z.string(), z.string()),
  compliant: z.boolean(),
})

export type DiversificationReport = z.infer<typeof DiversificationReportSchema>
