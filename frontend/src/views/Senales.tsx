import { useState } from 'react'
import { useSignalHistory, useSignalsDashboard } from '../api/queries/signals'
import { formatScore } from '../api/money'
import { compareDecimalStrings } from '../api/decimalCompare'
import { ScoreBar, Surface, Tag } from '../design/primitives'
import { DataTable, type DataTableColumn, type DataTableSort } from '../components/table'
import { EmptyState, ErrorState, LoadingState } from '../components/state'
import type { SignalSnapshot, TickerSignal } from '../api/schemas/signals'

// The six per-source scores the contract defines, in the order the table
// renders them. Each is a key into `SignalSnapshotDto.scores`, a
// `Record<string, string>` — with `noUncheckedIndexedAccess` on, every
// lookup below returns `string | undefined` and is handled as such, never
// assumed present.
const SOURCES = ['TECHNICAL', 'NEWS', 'ANALYST', 'INSIDER', 'SOCIAL', 'WSB'] as const
type Source = (typeof SOURCES)[number]

/** `usedSources.length === 0` is the one marker this whole view reads — never the score value or the level string, which the backend's threshold table still fills in as `0.00`/`STRONG_SELL` for a snapshot with no sources at all. */
function hasData(signal: TickerSignal): boolean {
  return signal.score.usedSources.length > 0
}

/** A dash for a source that did not contribute to this ticker's score — absence, not a zero. */
function DashCell() {
  return (
    <span aria-hidden="true" className="text-fg-subtle">
      —
    </span>
  )
}

function SourceScoreCell({ snapshot, signal, source }: { snapshot: SignalSnapshot; signal: TickerSignal; source: Source }) {
  if (!hasData(signal)) return <DashCell />
  const value = snapshot.scores[source]
  if (value === undefined) return <DashCell />
  return <span className="block w-full text-right font-mono tabular-nums text-fg">{formatScore(value)}</span>
}

/** The combined score with its `ScoreBar`, or the "sin datos" marker in its place — never the zero-source `0.00` the backend still computes. */
function CombinedScoreCell({ signal }: { signal: TickerSignal }) {
  if (!hasData(signal)) return <DashCell />
  return <ScoreBar score={formatScore(signal.score.value)} />
}

/** The level `Tag`, or the "Sin datos" marker that replaces it entirely for a zero-source signal — this is the one place "Sin datos" is rendered per row. */
function LevelCell({ signal }: { signal: TickerSignal }) {
  if (!hasData(signal)) return <Tag label="Sin datos" tone="accent" />
  return <Tag label={signal.score.level} />
}

/** How many of the six sources contributed, as `n/6` — the whole reason `usedSources` is in the contract: a score built from three sources is a weaker claim than the same number built from six. */
function SourceCountCell({ signal }: { signal: TickerSignal }) {
  if (!hasData(signal)) return <DashCell />
  return (
    <span className="block w-full text-right font-mono tabular-nums text-fg-muted">
      {signal.score.usedSources.length}/{SOURCES.length}
    </span>
  )
}

function TickerCell({ signal, onOpenHistory }: { signal: TickerSignal; onOpenHistory: (ticker: string) => void }) {
  return (
    <div className="flex flex-col items-start gap-0.5">
      <div className="flex items-center gap-2">
        <span className="font-medium text-fg">{signal.ticker}</span>
        <button
          type="button"
          onClick={() => onOpenHistory(signal.ticker)}
          aria-label={`Ver historial de ${signal.ticker}`}
          className="rounded-sm font-sans text-xs text-accent underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-ground"
        >
          Historial
        </button>
      </div>
      {hasData(signal) && <span className="font-mono text-xs text-fg-subtle">{signal.snapshot.snapshotDate}</span>}
    </div>
  )
}

const SOURCE_HEADER: Record<Source, string> = {
  TECHNICAL: 'Technical',
  NEWS: 'News',
  ANALYST: 'Analyst',
  INSIDER: 'Insider',
  SOCIAL: 'Social',
  WSB: 'WSB',
}

function sortValueFor(signal: TickerSignal, key: string): string {
  if (key === 'combined') return signal.score.value
  if ((SOURCES as readonly string[]).includes(key)) {
    return signal.snapshot.scores[key] ?? '-1'
  }
  return ''
}

function buildColumns(onOpenHistory: (ticker: string) => void): DataTableColumn<TickerSignal>[] {
  const sourceColumns: DataTableColumn<TickerSignal>[] = SOURCES.map((source) => ({
    key: source,
    header: SOURCE_HEADER[source],
    sortable: true,
    width: '4.75rem',
    render: (signal) => <SourceScoreCell snapshot={signal.snapshot} signal={signal} source={source} />,
  }))

  return [
    {
      key: 'ticker',
      header: 'Ticker',
      align: 'left',
      width: '7rem',
      render: (signal) => <TickerCell signal={signal} onOpenHistory={onOpenHistory} />,
    },
    ...sourceColumns,
    {
      key: 'combined',
      header: 'Score',
      sortable: true,
      width: '6.5rem',
      render: (signal) => <CombinedScoreCell signal={signal} />,
    },
    {
      key: 'level',
      header: 'Nivel',
      width: '6rem',
      render: (signal) => <LevelCell signal={signal} />,
    },
    {
      key: 'sources',
      header: 'Fuentes',
      width: '3.5rem',
      render: (signal) => <SourceCountCell signal={signal} />,
    },
  ]
}

function sortSignals(signals: ReadonlyArray<TickerSignal>, sort: DataTableSort | null): TickerSignal[] {
  if (sort === null) return [...signals]
  const sorted = [...signals].sort((a, b) =>
    compareDecimalStrings(sortValueFor(a, sort.key), sortValueFor(b, sort.key)),
  )
  return sort.direction === 'desc' ? sorted.reverse() : sorted
}

function AverageScoreTile({ averageScore, hasAnyScoredTicker }: { averageScore: string; hasAnyScoredTicker: boolean }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">Score promedio</span>
      {hasAnyScoredTicker ? (
        <span className="block w-full text-right font-mono text-lg tabular-nums text-fg">
          {formatScore(averageScore)}
        </span>
      ) : (
        <span className="font-sans text-xs text-fg-subtle">
          No hay tickers con datos todavía para calcular el promedio.
        </span>
      )}
    </div>
  )
}

function LevelCountsTile({ levelCounts }: { levelCounts: Record<string, number> }) {
  const entries = Object.entries(levelCounts)
  return (
    <div className="flex flex-col gap-1">
      <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">Niveles</span>
      {entries.length > 0 ? (
        <div className="flex flex-wrap justify-end gap-1">
          {entries.map(([level, count]) => (
            <Tag key={level} label={`${level}: ${count}`} />
          ))}
        </div>
      ) : (
        <span className="font-sans text-xs text-fg-subtle">Sin niveles calculados todavía.</span>
      )}
    </div>
  )
}

/**
 * Trends per source as tiny inline-SVG sparklines, not Recharts: a
 * handful of daily points per ticker does not need a charting library,
 * and importing one here (even lazily) would still mean this route pays
 * for it. The one numeric coercion below (`Number(point)` for a pixel
 * y-coordinate) mirrors what `ScoreBar` already does for its own bar
 * width — layout math, not a new business figure — and every value is
 * also printed as the exact string the API returned, so nothing is
 * conveyed by the line alone.
 */
function Sparkline({ points }: { points: ReadonlyArray<string> }) {
  const width = 160
  const height = 32
  const coords = points.map((point, index) => {
    const x = points.length > 1 ? (index / (points.length - 1)) * width : width / 2
    const numeric = Number(point)
    const clamped = Number.isFinite(numeric) ? Math.min(Math.max(numeric, 0), 10) : 0
    const y = height - (clamped / 10) * height
    return `${x},${y}`
  })
  return (
    <svg viewBox={`0 0 ${width} ${height}`} width={width} height={height} aria-hidden="true">
      <polyline points={coords.join(' ')} fill="none" stroke="currentColor" strokeWidth={1.5} className="text-accent" />
    </svg>
  )
}

function SourceTrendRow({ source, history }: { source: Source; history: ReadonlyArray<SignalSnapshot> }) {
  const points = history
    .map((day) => day.scores[source])
    .filter((value): value is string => value !== undefined)

  if (points.length === 0) {
    return (
      <li className="flex items-center justify-between gap-3 py-1">
        <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">{SOURCE_HEADER[source]}</span>
        <span className="font-sans text-xs text-fg-subtle">Sin datos para esta fuente</span>
      </li>
    )
  }

  const first = points[0]
  const last = points[points.length - 1]

  return (
    <li className="flex items-center justify-between gap-3 py-1">
      <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">{SOURCE_HEADER[source]}</span>
      <Sparkline points={points} />
      <span className="font-mono text-xs tabular-nums text-fg-subtle">
        {first} → {last}
      </span>
    </li>
  )
}

function HistoryPanel({ ticker, onClose }: { ticker: string; onClose: () => void }) {
  const today = new Date()
  const to = today.toISOString().slice(0, 10)
  const from = new Date(today.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
  const { data, isPending, isError, error, refetch } = useSignalHistory(ticker, from, to)

  return (
    <Surface level={1} data-testid="signal-history-chart" className="flex flex-col gap-3 p-4">
      <div className="flex items-center justify-between">
        <h2 className="font-mono text-sm font-semibold uppercase tracking-wide text-fg-muted">
          Historial de {ticker}
        </h2>
        <button
          type="button"
          onClick={onClose}
          className="rounded-sm font-sans text-xs text-fg-muted hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-ground"
        >
          Cerrar
        </button>
      </div>
      {isPending && <LoadingState rows={2} />}
      {isError && <ErrorState message={error instanceof Error ? error.message : undefined} onRetry={() => refetch()} />}
      {!isPending && !isError && data.length === 0 && (
        <EmptyState message="Todavía no hay historial persistido para este ticker." />
      )}
      {!isPending && !isError && data.length > 0 && (
        <ul className="flex flex-col divide-y divide-border/60">
          {SOURCES.map((source) => (
            <SourceTrendRow key={source} source={source} history={data} />
          ))}
        </ul>
      )}
    </Surface>
  )
}

export function Senales() {
  const { data, isPending, isError, error, refetch } = useSignalsDashboard()
  const [openedTicker, setOpenedTicker] = useState<string | null>(null)
  // Defaults to combined score descending — matching the API's own
  // order — as a real instruction, not an accident that merely holds
  // because the backend happens to pre-sort: sortSignals below always
  // re-sorts, and the Score header's aria-sort reflects this from the
  // very first render instead of reporting 'none' while the rows are
  // visibly ordered.
  const [sort, setSort] = useState<DataTableSort | null>({ key: 'combined', direction: 'desc' })

  if (isPending) {
    return <LoadingState rows={8} />
  }

  if (isError) {
    const message = error instanceof Error ? error.message : undefined
    return <ErrorState message={message} onRetry={() => refetch()} />
  }

  if (data.signals.length === 0) {
    return <EmptyState message="No hay señales calculadas todavía." />
  }

  const columns = buildColumns((ticker) => setOpenedTicker(ticker))
  const rows = sortSignals(data.signals, sort)
  const hasAnyScoredTicker = data.signals.some(hasData)

  function handleSortChange(key: string) {
    setSort((previous) => {
      const isSameColumn = previous?.key === key
      return { key, direction: isSameColumn && previous?.direction === 'desc' ? 'asc' : 'desc' }
    })
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-mono text-xl font-semibold text-fg">Señales</h1>
      <Surface level={1} className="grid grid-cols-1 gap-4 p-4 sm:grid-cols-2">
        <AverageScoreTile averageScore={data.averageScore} hasAnyScoredTicker={hasAnyScoredTicker} />
        <LevelCountsTile levelCounts={data.levelCounts} />
      </Surface>
      {data.tickersWithoutData.length > 0 && (
        <p role="note" className="font-sans text-sm text-fg-muted">
          Sin datos (snapshot vacío, ningún origen aportó un puntaje): {data.tickersWithoutData.join(', ')}.
        </p>
      )}
      <DataTable
        caption="Señales por ticker: puntaje combinado y por fuente"
        columns={columns}
        rows={rows}
        getRowKey={(signal) => signal.ticker}
        getRowTestId={(signal) => `signal-row-${signal.ticker}`}
        sort={sort}
        onSortChange={handleSortChange}
        density="dense"
        rowClassName={(signal) => (hasData(signal) ? '' : 'bg-surface-2/60')}
      />
      {openedTicker !== null && <HistoryPanel ticker={openedTicker} onClose={() => setOpenedTicker(null)} />}
    </div>
  )
}
