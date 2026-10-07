import { z } from 'zod'
import { MoneySchema } from './common'

// Mirrors PositionDto.
export const PositionSchema = z.object({
  ticker: z.string(),
  brokerName: z.string(),
  quantity: z.string(),
  totalCost: MoneySchema,
  averageCost: MoneySchema,
  realizedPnl: MoneySchema,
  marketValue: MoneySchema,
  unrealizedPnl: MoneySchema,
  unrealizedPnlPercent: z.string(),
  dayChangePercent: z.string(),
  priced: z.boolean(),
  costInBaseCurrency: MoneySchema,
  marketValueInBaseCurrency: MoneySchema,
})

export type Position = z.infer<typeof PositionSchema>

// Mirrors PortfolioResponse, returned by GET /portfolio.
export const PortfolioResponseSchema = z.object({
  positions: z.array(PositionSchema),
  totalInvested: MoneySchema,
  totalMarketValue: MoneySchema,
  totalUnrealizedPnl: MoneySchema,
  unpricedTickers: z.array(z.string()),
  baseCurrency: z.string(),
})

export type PortfolioResponse = z.infer<typeof PortfolioResponseSchema>
