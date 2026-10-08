import { z } from 'zod'

// Mirrors FundamentalsDto, returned by GET /fundamentals/{ticker}. Every
// metric below is nullable — not just the ones a sample happened to show
// null, but structurally: all thirteen are plain `BigDecimal` in the
// `Fundamentals` domain model, and `DecimalStrings.of(BigDecimal)` returns
// null whenever the value is null, so any BigDecimal field with no
// non-null guarantee is nullable on the wire regardless of what any one
// sample shows. Confirmed against GET /fundamentals/NU (revenueCagr5y,
// epsCagr5y, dividendYield null) and GET /fundamentals/QQQ (an ETF with
// hasData: true where all but `beta` are null) — `beta` just hadn't been
// observed null yet, not structurally exempt. `hasData` therefore cannot
// be read as "every metric is present" — see the views that consume this
// schema for the per-metric presence check that implies.
export const FundamentalsSchema = z.object({
  ticker: z.string(),
  isEtf: z.boolean(),
  hasData: z.boolean(),
  per: z.string().nullable(),
  peg: z.string().nullable(),
  priceToBook: z.string().nullable(),
  priceToSales: z.string().nullable(),
  evToEbitda: z.string().nullable(),
  roe: z.string().nullable(),
  netMargin: z.string().nullable(),
  revenueCagr5y: z.string().nullable(),
  epsCagr5y: z.string().nullable(),
  debtToEquity: z.string().nullable(),
  beta: z.string().nullable(),
  dividendYield: z.string().nullable(),
  currentRatio: z.string().nullable(),
})

export type Fundamentals = z.infer<typeof FundamentalsSchema>
