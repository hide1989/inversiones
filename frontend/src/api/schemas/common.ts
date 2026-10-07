import { z } from 'zod'

// Every monetary amount is serialised by the backend as a decimal string,
// never a JSON number, so that scale is never lost to float precision.
// This is the single schema every response's Money fields reference.
export const CurrencySchema = z.enum(['USD', 'COP'])

export const MoneySchema = z.object({
  amount: z.string(),
  currency: CurrencySchema,
})

export type Currency = z.infer<typeof CurrencySchema>
export type Money = z.infer<typeof MoneySchema>
