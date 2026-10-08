import { useState } from 'react'
import { useEarnings } from '../api/queries/earnings'
import { formatDecimalString } from '../api/money'
import { compareDecimalStrings } from '../api/decimalCompare'
import { NoDataCell } from '../design/primitives'
import { DataTable, type DataTableColumn, type DataTableSort } from '../components/table'
import { EmptyState, ErrorState, LoadingState } from '../components/state'
import type { EarningsEvent } from '../api/schemas/earnings'

/**
 * `estimatedEps` and `historicalSurprise` are decimal strings with no
 * currency attached, so `formatDecimalString` (not `formatMoney`, which
 * requires a `Money` object) fixes their width without any numeric
 * coercion — the same primitive `formatPercent`/`formatScore` build on.
 * Both fields are nullable on the wire (an unguarded `BigDecimal` in
 * `EarningsEvent`, same as every other decimal field `DecimalStrings.of`
 * can emit as null) — a null renders as `NoDataCell`, never coerced into
 * a displayable string.
 */
function formatMetric(value: string): string {
  return formatDecimalString(value, 2, false)
}

function MetricCell({ value }: { value: string | null }) {
  if (value === null) return <NoDataCell />
  return <span className="block w-full text-right font-mono tabular-nums text-fg">{formatMetric(value)}</span>
}

/**
 * `daysRemaining` is the one field in this contract the backend sends as
 * a real JSON number (`z.number()`), not a decimal string — so comparing
 * two of them directly is comparing integers the API already gave us as
 * numbers, never a coercion of a string the money.ts rule exists to
 * guard against.
 *
 * `estimatedEps` and `historicalSurprise` can be null, and a real EPS or
 * surprise value can legitimately be negative, so there is no safe
 * sentinel string to substitute (unlike Watchlist's scores, which are
 * never negative). A null instead sorts last unconditionally — not
 * flipped by `direction` — so it reads as "no data" parked at the end of
 * the table regardless of which way the column is sorted.
 */
function compareNullableDecimal(a: string | null, b: string | null, direction: 'asc' | 'desc'): number {
  if (a === null && b === null) return 0
  if (a === null) return 1
  if (b === null) return -1
  const comparison = compareDecimalStrings(a, b)
  return direction === 'desc' ? -comparison : comparison
}

function sortEarnings(events: ReadonlyArray<EarningsEvent>, sort: DataTableSort | null): EarningsEvent[] {
  if (sort === null) return [...events]
  const direction = sort.direction
  return [...events].sort((a, b) => {
    if (sort.key === 'daysRemaining') {
      const comparison = a.daysRemaining - b.daysRemaining
      return direction === 'desc' ? -comparison : comparison
    }
    if (sort.key === 'estimatedEps') return compareNullableDecimal(a.estimatedEps, b.estimatedEps, direction)
    if (sort.key === 'historicalSurprise') {
      return compareNullableDecimal(a.historicalSurprise, b.historicalSurprise, direction)
    }
    return 0
  })
}

function buildColumns(): DataTableColumn<EarningsEvent>[] {
  return [
    {
      key: 'ticker',
      header: 'Ticker',
      align: 'left',
      width: '6rem',
      render: (event) => <span className="font-medium text-fg">{event.ticker}</span>,
    },
    {
      key: 'reportDate',
      header: 'Fecha',
      align: 'left',
      width: '7rem',
      render: (event) => <span className="font-mono text-sm text-fg-muted">{event.reportDate}</span>,
    },
    {
      key: 'daysRemaining',
      header: 'Días restantes',
      sortable: true,
      width: '7rem',
      render: (event) => (
        <span className="block w-full text-right font-mono tabular-nums text-fg">{event.daysRemaining}</span>
      ),
    },
    {
      key: 'estimatedEps',
      header: 'EPS estimado',
      sortable: true,
      width: '7rem',
      render: (event) => <MetricCell value={event.estimatedEps} />,
    },
    {
      key: 'historicalSurprise',
      header: 'Sorpresa histórica',
      sortable: true,
      width: '8rem',
      render: (event) => <MetricCell value={event.historicalSurprise} />,
    },
  ]
}

export function Earnings() {
  const { data, isPending, isError, error, refetch } = useEarnings()
  // Proximity — soonest earnings report first — is this view's whole
  // reason to exist, so it is the default sort from the first render, not
  // an accident of whatever order the backend happens to send.
  const [sort, setSort] = useState<DataTableSort | null>({ key: 'daysRemaining', direction: 'asc' })

  if (isPending) {
    return <LoadingState rows={5} />
  }

  if (isError) {
    const message = error instanceof Error ? error.message : undefined
    return <ErrorState message={message} onRetry={() => refetch()} />
  }

  if (data.length === 0) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="font-mono text-xl font-semibold text-fg">Earnings</h1>
        <EmptyState message="No hay reportes de resultados próximos." />
      </div>
    )
  }

  const rows = sortEarnings(data, sort)

  function handleSortChange(key: string) {
    setSort((previous) => {
      const isSameColumn = previous?.key === key
      return { key, direction: isSameColumn && previous?.direction === 'asc' ? 'desc' : 'asc' }
    })
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-mono text-xl font-semibold text-fg">Earnings</h1>
      <DataTable
        caption="Próximos reportes de resultados, ordenados por proximidad"
        columns={buildColumns()}
        rows={rows}
        getRowKey={(event) => event.ticker}
        getRowTestId={(event) => `earnings-row-${event.ticker}`}
        sort={sort}
        onSortChange={handleSortChange}
      />
    </div>
  )
}
