import { z } from 'zod'

// Mirrors AiSynthesisDto.
export const AiSynthesisItemSchema = z.object({
  ticker: z.string(),
  snapshotDate: z.string(),
  iaScore: z.string(),
  signal: z.string(),
  conviction: z.string(),
  horizon: z.string(),
  summary: z.string(),
  catalysts: z.array(z.string()),
  risks: z.array(z.string()),
})

export type AiSynthesisItem = z.infer<typeof AiSynthesisItemSchema>

// Mirrors AiSynthesisResponse, returned by GET /ai-synthesis.
export const AiSynthesisResponseSchema = z.object({
  withData: z.array(AiSynthesisItemSchema),
  withoutData: z.array(z.string()),
  averageScore: z.string(),
})

export type AiSynthesisResponse = z.infer<typeof AiSynthesisResponseSchema>
