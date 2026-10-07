import { z } from 'zod'
import { CurrencySchema, MoneySchema } from './common'

// Mirrors TransactionRequest, the body for POST and PUT /transactions.
export const TransactionRequestSchema = z.object({
  ticker: z.string(),
  brokerName: z.string(),
  type: z.enum(['BUY', 'SELL']),
  quantity: z.string(),
  unitPrice: z.string(),
  currency: CurrencySchema,
  fees: z.string().optional(),
  tradedAt: z.iso.date(),
})

export type TransactionRequest = z.infer<typeof TransactionRequestSchema>

// Mirrors TransactionResponse, returned by GET/POST/PUT /transactions.
export const TransactionResponseSchema = z.object({
  id: z.number(),
  ticker: z.string(),
  brokerName: z.string(),
  type: z.string(),
  quantity: z.string(),
  unitPrice: MoneySchema,
  fees: MoneySchema,
  tradedAt: z.iso.date(),
  grossAmount: MoneySchema,
  netAmount: MoneySchema,
})

export type TransactionResponse = z.infer<typeof TransactionResponseSchema>

export const TransactionListResponseSchema = z.array(TransactionResponseSchema)
