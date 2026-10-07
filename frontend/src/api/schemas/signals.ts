import { z } from 'zod'

// Mirrors SignalSnapshotDto.
export const SignalSnapshotSchema = z.object({
  ticker: z.string(),
  snapshotDate: z.iso.date(),
  scores: z.record(z.string(), z.string()),
})

export type SignalSnapshot = z.infer<typeof SignalSnapshotSchema>

// Mirrors CombinedScoreDto.
export const CombinedScoreSchema = z.object({
  value: z.string(),
  level: z.string(),
  usedSources: z.array(z.string()),
})

export type CombinedScore = z.infer<typeof CombinedScoreSchema>

// Mirrors TickerSignalDto.
export const TickerSignalSchema = z.object({
  ticker: z.string(),
  snapshot: SignalSnapshotSchema,
  score: CombinedScoreSchema,
})

export type TickerSignal = z.infer<typeof TickerSignalSchema>

// Mirrors SignalsDashboardResponse, returned by GET /signals.
export const SignalsDashboardResponseSchema = z.object({
  signals: z.array(TickerSignalSchema),
  averageScore: z.string(),
  levelCounts: z.record(z.string(), z.number()),
  tickersWithoutData: z.array(z.string()),
})

export type SignalsDashboardResponse = z.infer<typeof SignalsDashboardResponseSchema>

// Mirrors the array returned by GET /signals/history.
export const SignalHistoryResponseSchema = z.array(SignalSnapshotSchema)
