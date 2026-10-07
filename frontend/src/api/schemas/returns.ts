import { z } from 'zod'
import { MoneySchema } from './common'

// Mirrors ExchangeRateDto.
export const ExchangeRateSchema = z.object({
  base: z.string(),
  quote: z.string(),
  rate: z.string(),
  rateDate: z.string(),
})

export type ExchangeRate = z.infer<typeof ExchangeRateSchema>

// Mirrors ReturnSegmentDto.
export const ReturnSegmentSchema = z.object({
  segment: z.enum(['USD_ASSETS', 'TRII_ADR', 'BVC_COLOMBIA']),
  invested: MoneySchema,
  marketValue: MoneySchema,
  assetGain: MoneySchema,
})

export type ReturnSegment = z.infer<typeof ReturnSegmentSchema>

// Mirrors TotalReturnResponse, returned by GET /portfolio/returns.
//
// When the exchange-rate source is unreachable the backend deliberately
// returns `currencyEffectAvailable: false` with `currentRate` and the
// three effect fields as `null` — that is not malformed, it is the
// backend saying "the rate is unknown" rather than lying with a zero.
// This schema must accept that shape as valid.
export const TotalReturnResponseSchema = z.object({
  segments: z.array(ReturnSegmentSchema),
  baseRate: ExchangeRateSchema,
  currentRate: ExchangeRateSchema.nullable(),
  currencyEffectAvailable: z.boolean(),
  exchangeEffect: MoneySchema.nullable(),
  assetGainInCop: MoneySchema.nullable(),
  totalReturnInCop: MoneySchema.nullable(),
})

export type TotalReturnResponse = z.infer<typeof TotalReturnResponseSchema>
