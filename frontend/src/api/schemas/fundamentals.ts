import { z } from 'zod'

// Mirrors FundamentalsDto, returned by GET /fundamentals/{ticker}.
export const FundamentalsSchema = z.object({
  ticker: z.string(),
  isEtf: z.boolean(),
  hasData: z.boolean(),
  per: z.string(),
  peg: z.string(),
  priceToBook: z.string(),
  priceToSales: z.string(),
  evToEbitda: z.string(),
  roe: z.string(),
  netMargin: z.string(),
  revenueCagr5y: z.string(),
  epsCagr5y: z.string(),
  debtToEquity: z.string(),
  beta: z.string(),
  dividendYield: z.string(),
  currentRatio: z.string(),
})

export type Fundamentals = z.infer<typeof FundamentalsSchema>
