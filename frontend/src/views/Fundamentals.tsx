import { useFundamentals } from '../api/queries/fundamentals'
import { formatDecimalString } from '../api/money'
import { Surface } from '../design/primitives'
import { EmptyState, ErrorState, LoadingState } from '../components/state'
import type { Fundamentals as FundamentalsData } from '../api/schemas/fundamentals'

/** A metric key from `FundamentalsData`, excluding the non-metric fields. */
type MetricKey = 'per' | 'peg' | 'priceToBook' | 'priceToSales' | 'evToEbitda' | 'roe' | 'netMargin' |
  'revenueCagr5y' | 'epsCagr5y' | 'debtToEquity' | 'currentRatio' | 'beta' | 'dividendYield'

const METRIC_LABEL: Record<MetricKey, string> = {
  per: 'P/E',
  peg: 'PEG',
  priceToBook: 'P/B',
  priceToSales: 'P/S',
  evToEbitda: 'EV/EBITDA',
  roe: 'ROE',
  netMargin: 'Margen neto',
  revenueCagr5y: 'CAGR ingresos (5a)',
  epsCagr5y: 'CAGR EPS (5a)',
  debtToEquity: 'Deuda/Patrimonio',
  currentRatio: 'Razón corriente',
  beta: 'Beta',
  dividendYield: 'Dividend yield',
}

// A stock's fundamentals carry per-company multiples a fund has no
// equivalent for (there is no single P/E or ROE for a basket of
// holdings) — so these only apply when `isEtf` is false.
const STOCK_ONLY_METRICS: MetricKey[] = [
  'per', 'peg', 'priceToBook', 'priceToSales', 'evToEbitda', 'roe',
  'netMargin', 'revenueCagr5y', 'epsCagr5y', 'debtToEquity', 'currentRatio',
]

// Beta and dividend yield are meaningful for both a stock and a fund.
const COMMON_METRICS: MetricKey[] = ['beta', 'dividendYield']

/**
 * Which metric keys are meaningful for this instrument kind. `isEtf`
 * picks the set — never `hasData`, which only says the ticker has *some*
 * fundamentals row, not that every metric in it is populated (the live
 * QQQ response has `hasData: true` with eleven of thirteen metrics null).
 */
function metricsFor(isEtf: boolean): MetricKey[] {
  return isEtf ? COMMON_METRICS : [...STOCK_ONLY_METRICS, ...COMMON_METRICS]
}

function MetricRow({ metricKey, value }: { metricKey: MetricKey; value: string }) {
  return (
    <div data-testid={`fundamentals-metric-${metricKey}`} className="flex items-center justify-between gap-3 py-1">
      <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">{METRIC_LABEL[metricKey]}</span>
      {/* The backend leaks double-precision artifacts into these strings
          (e.g. roe: "30.259999999999998") — formatDecimalString fixes the
          display width without ever parsing the value as a float. */}
      <span className="font-mono text-sm tabular-nums text-fg">{formatDecimalString(value, 2, false)}</span>
    </div>
  )
}

/**
 * The metric grid, or nothing. `hasData` alone cannot drive this — QQQ
 * has `hasData: true` with every multiple null but `beta` — so presence
 * is checked per metric, and if the instrument-appropriate set is
 * entirely null, no empty grid renders at all; the no-metrics message
 * below takes its place instead.
 */
function MetricGrid({ data }: { data: FundamentalsData }) {
  const keys = metricsFor(data.isEtf)
  const present = keys.filter((key) => data[key] !== null)

  if (present.length === 0) {
    return (
      <EmptyState message="No hay métricas de fundamentales disponibles para este ticker todavía." />
    )
  }

  return (
    <div data-testid="fundamentals-metric-grid" className="grid grid-cols-1 gap-x-6 gap-y-0 sm:grid-cols-2">
      {present.map((key) => {
        const value = data[key]
        if (value === null) return null
        return <MetricRow key={key} metricKey={key} value={value} />
      })}
    </div>
  )
}

export interface FundamentalsPanelProps {
  ticker: string
  onClose: () => void
}

/**
 * Fetched per ticker on demand — not a route, so it carries no view
 * heading of its own; the caller (Watchlist) supplies the surrounding
 * context and decides when to mount it.
 */
export function FundamentalsPanel({ ticker, onClose }: FundamentalsPanelProps) {
  const { data, isPending, isError, error, refetch } = useFundamentals(ticker)

  return (
    <Surface level={1} data-testid="fundamentals-panel" className="flex flex-col gap-3 p-4">
      <div className="flex items-center justify-between">
        <h2 className="font-mono text-sm font-semibold uppercase tracking-wide text-fg-muted">
          Fundamentales de {ticker}
        </h2>
        <button
          type="button"
          onClick={onClose}
          className="rounded-sm font-sans text-xs text-fg-muted hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-ground"
        >
          Cerrar
        </button>
      </div>
      {isPending && <LoadingState rows={3} />}
      {isError && <ErrorState message={error instanceof Error ? error.message : undefined} onRetry={() => refetch()} />}
      {!isPending && !isError && !data.hasData && (
        <EmptyState message={`No hay datos de fundamentales para ${ticker}.`} />
      )}
      {!isPending && !isError && data.hasData && <MetricGrid data={data} />}
    </Surface>
  )
}
