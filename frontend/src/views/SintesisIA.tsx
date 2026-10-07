import { useAiSynthesis } from '../api/queries/aiSynthesis'
import { formatScore } from '../api/money'
import { Surface, Tag } from '../design/primitives'
import { EmptyState, ErrorState, LoadingState } from '../components/state'
import type { AiSynthesisItem, AiSynthesisResponse } from '../api/schemas/aiSynthesis'

/**
 * Whether the average is a real figure or not depends only on
 * `withData.length`, never on `averageScore` itself — the live backend
 * sends `averageScore: "0.00"` with zero scored tickers (an average over
 * an empty set), and that string must never be mistaken for a measured
 * score of zero.
 */
function hasAnyScoredTicker(data: AiSynthesisResponse): boolean {
  return data.withData.length > 0
}

/**
 * The average score tile. When no ticker has AI data yet, this renders
 * no figure at all — not even "0.00" — and says explicitly that no
 * average can be computed. When some tickers do have data, the average
 * is labelled as covering only those tickers, with the excluded ones
 * named, so it never reads as if it covered the whole portfolio.
 */
function AverageScoreCard({ data }: { data: AiSynthesisResponse }) {
  if (!hasAnyScoredTicker(data)) {
    return (
      <Surface level={1} className="flex flex-col gap-2 p-4">
        <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">Score promedio IA</span>
        <p className="font-sans text-sm text-fg-subtle">
          Ningún ticker tiene datos de síntesis IA todavía, así que no hay promedio para calcular.
        </p>
      </Surface>
    )
  }

  return (
    <Surface level={1} className="flex flex-col gap-2 p-4">
      <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">Score promedio IA</span>
      <span className="font-mono text-2xl tabular-nums text-fg">{formatScore(data.averageScore)}</span>
      <p className="font-sans text-xs text-fg-subtle">
        Promedio calculado sobre {data.withData.length} ticker{data.withData.length === 1 ? '' : 's'} con datos de
        síntesis IA.
        {data.withoutData.length > 0 &&
          ` Excluye ${data.withoutData.length} ticker${data.withoutData.length === 1 ? '' : 's'} sin datos: ${data.withoutData.join(', ')}.`}
      </p>
    </Surface>
  )
}

const SIGNAL_TONE: Record<string, 'positive' | 'negative' | 'accent' | 'neutral'> = {
  BUY: 'positive',
  STRONG_BUY: 'positive',
  SELL: 'negative',
  STRONG_SELL: 'negative',
  HOLD: 'accent',
}

function TagList({ label, items }: { label: string; items: ReadonlyArray<string> }) {
  if (items.length === 0) return null
  return (
    <div className="flex flex-col gap-1">
      <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">{label}</span>
      <ul className="flex flex-col gap-0.5 font-sans text-sm text-fg">
        {items.map((item) => (
          <li key={item}>• {item}</li>
        ))}
      </ul>
    </div>
  )
}

/**
 * One ticker's card. The summary is prose — the one place in this view
 * where `font-sans` (IBM Plex Sans) carries a paragraph rather than a
 * label, since every other figure here is `font-mono`.
 */
function SynthesisCard({ item }: { item: AiSynthesisItem }) {
  return (
    <Surface level={1} data-testid={`synthesis-card-${item.ticker}`} className="flex flex-col gap-3 p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className="font-mono text-base font-semibold text-fg">{item.ticker}</span>
          <Tag label={item.signal} tone={SIGNAL_TONE[item.signal] ?? 'neutral'} />
        </div>
        <span className="font-mono text-xl tabular-nums text-fg">{formatScore(item.iaScore)}</span>
      </div>
      <div className="flex flex-wrap gap-2">
        <Tag label={`Convicción: ${item.conviction}`} />
        <Tag label={`Horizonte: ${item.horizon}`} />
        <span className="font-mono text-xs text-fg-subtle">{item.snapshotDate}</span>
      </div>
      <p className="font-sans text-sm leading-relaxed text-fg">{item.summary}</p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <TagList label="Catalizadores" items={item.catalysts} />
        <TagList label="Riesgos" items={item.risks} />
      </div>
    </Surface>
  )
}

export function SintesisIA() {
  const { data, isPending, isError, error, refetch } = useAiSynthesis()

  if (isPending) {
    return <LoadingState rows={6} />
  }

  if (isError) {
    const message = error instanceof Error ? error.message : undefined
    return <ErrorState message={message} onRetry={() => refetch()} />
  }

  if (data.withData.length === 0 && data.withoutData.length === 0) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="font-mono text-xl font-semibold text-fg">Síntesis IA</h1>
        <EmptyState message="No hay tickers configurados para síntesis IA." />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-mono text-xl font-semibold text-fg">Síntesis IA</h1>
      <AverageScoreCard data={data} />
      {data.withData.length > 0 && (
        <div className="flex flex-col gap-4">
          {data.withData.map((item) => (
            <SynthesisCard key={item.ticker} item={item} />
          ))}
        </div>
      )}
      {data.withoutData.length > 0 && (
        <p role="note" className="font-sans text-sm text-fg-muted">
          Sin datos de síntesis IA: {data.withoutData.join(', ')}.
        </p>
      )}
      {data.withData.length === 0 && (
        <EmptyState message="Ningún ticker tiene síntesis IA calculada todavía." />
      )}
    </div>
  )
}
