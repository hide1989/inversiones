import type { ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { usePortfolio } from '../api/queries/portfolio'
import { formatMoney, formatPercent, formatQuantity } from '../api/money'
import { compareDecimalStrings } from '../api/decimalCompare'
import { magnitudeOf, signOf } from '../api/decimalSign'
import { Delta, Figure, Surface, Tag } from '../design/primitives'
import { DataTable, type DataTableColumn, type DataTableSort } from '../components/table'
import { EmptyState, ErrorState, LoadingState } from '../components/state'
import { TotalReturnPanel } from './TotalReturnPanel'
import type { PortfolioResponse, Position } from '../api/schemas/portfolio'

// Only numeric columns are sortable — ticker and broker are identity, not
// a quantity. Each entry extracts the exact decimal string the column
// renders, so the comparator never touches a reformatted value.
const SORT_VALUE: Record<string, (position: Position) => string> = {
  quantity: (position) => position.quantity,
  averageCost: (position) => position.averageCost.amount,
  marketValue: (position) => position.marketValue.amount,
  dayChangePercent: (position) => position.dayChangePercent,
  unrealizedPnl: (position) => position.unrealizedPnl.amount,
  unrealizedPnlPercent: (position) => position.unrealizedPnlPercent,
}

function parseSort(params: URLSearchParams): DataTableSort | null {
  const key = params.get('sort')
  if (key === null || !(key in SORT_VALUE)) return null
  return { key, direction: params.get('dir') === 'asc' ? 'asc' : 'desc' }
}

function sortPositions(positions: ReadonlyArray<Position>, sort: DataTableSort | null): Position[] {
  if (sort === null) return [...positions]
  const valueOf = SORT_VALUE[sort.key]
  if (valueOf === undefined) return [...positions]
  const sorted = [...positions].sort((a, b) => compareDecimalStrings(valueOf(a), valueOf(b)))
  return sort.direction === 'desc' ? sorted.reverse() : sorted
}

/**
 * A `priced: false` position: the backend sets this flag both for a
 * missing quote and for a peso position with no exchange rate to
 * convert — one boolean, two causes — so the UI states both rather than
 * guessing which one applies. `compact` renders the terse marker used in
 * the other valuation-dependent cells of the same row, so the sentence
 * itself appears once per row, not four times.
 */
function UnpricedCell({ compact = false }: { compact?: boolean }) {
  if (compact) {
    return (
      <span aria-hidden="true" className="text-fg-subtle">
        —
      </span>
    )
  }
  return (
    <div className="flex flex-col items-end gap-0.5">
      <Tag label="Sin valorar" tone="accent" />
      <span className="font-sans text-xs text-fg-subtle">Falta precio de mercado o tasa de cambio</span>
    </div>
  )
}

/**
 * A priced position's market value in its own currency, plus — only when
 * that currency differs from the portfolio's base currency — the
 * base-currency equivalent the backend already computed
 * (`marketValueInBaseCurrency`). This is the legibility rule 3 asks for:
 * the row shows pesos, the annotation shows what those pesos are worth in
 * the currency the totals use, and neither number is derived in the
 * browser.
 */
function MarketValueCell({ position, baseCurrency }: { position: Position; baseCurrency: string }) {
  if (!position.priced) {
    return <UnpricedCell />
  }
  return (
    <div className="flex flex-col items-end gap-0.5">
      <Figure value={formatMoney(position.marketValue, { withCurrency: false })} />
      {position.marketValue.currency !== baseCurrency && (
        <span className="font-mono text-xs tabular-nums text-fg-subtle">
          ≈ {formatMoney(position.marketValueInBaseCurrency)}
        </span>
      )}
    </div>
  )
}

function buildColumns(baseCurrency: string): DataTableColumn<Position>[] {
  return [
    {
      key: 'ticker',
      header: 'Ticker',
      align: 'left',
      render: (position) => <span className="font-medium text-fg">{position.ticker}</span>,
    },
    {
      key: 'broker',
      header: 'Broker',
      align: 'left',
      render: (position) => <Tag label={position.brokerName} />,
    },
    {
      key: 'quantity',
      header: 'Cantidad',
      sortable: true,
      render: (position) => <Figure value={formatQuantity(position.quantity)} />,
    },
    {
      key: 'averageCost',
      header: 'Costo prom.',
      sortable: true,
      render: (position) => <Figure value={formatMoney(position.averageCost, { withCurrency: false })} />,
    },
    {
      key: 'marketValue',
      header: 'Valor mercado',
      sortable: true,
      render: (position) => <MarketValueCell position={position} baseCurrency={baseCurrency} />,
    },
    {
      key: 'dayChangePercent',
      header: 'Día %',
      sortable: true,
      render: (position) =>
        position.priced ? (
          <Delta value={formatPercent(magnitudeOf(position.dayChangePercent))} sign={signOf(position.dayChangePercent)} />
        ) : (
          <UnpricedCell compact />
        ),
    },
    {
      key: 'unrealizedPnl',
      header: 'P&L no realizado',
      sortable: true,
      render: (position) =>
        position.priced ? (
          <Delta
            value={formatMoney(
              { amount: magnitudeOf(position.unrealizedPnl.amount), currency: position.unrealizedPnl.currency },
              { withCurrency: false },
            )}
            sign={signOf(position.unrealizedPnl.amount)}
          />
        ) : (
          <UnpricedCell compact />
        ),
    },
    {
      key: 'unrealizedPnlPercent',
      header: 'P&L %',
      sortable: true,
      render: (position) =>
        position.priced ? (
          <Delta
            value={formatPercent(magnitudeOf(position.unrealizedPnlPercent))}
            sign={signOf(position.unrealizedPnlPercent)}
          />
        ) : (
          <UnpricedCell compact />
        ),
    },
  ]
}

function Tile({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">{label}</span>
      {children}
    </div>
  )
}

/** Every figure here comes straight from `PortfolioResponse` — no total is ever summed in the browser. */
function SummaryTiles({ portfolio }: { portfolio: PortfolioResponse }) {
  return (
    <Surface level={1} className="grid grid-cols-2 gap-4 p-4 sm:grid-cols-4">
      <Tile label="Invertido">
        <Figure value={formatMoney(portfolio.totalInvested)} />
      </Tile>
      <Tile label="Valor hoy">
        <Figure value={formatMoney(portfolio.totalMarketValue)} />
      </Tile>
      <Tile label="P&L no realizado">
        <Delta
          value={formatMoney(
            { amount: magnitudeOf(portfolio.totalUnrealizedPnl.amount), currency: portfolio.totalUnrealizedPnl.currency },
            { withCurrency: false },
          )}
          sign={signOf(portfolio.totalUnrealizedPnl.amount)}
        />
      </Tile>
      <Tile label="Divisa base">
        <span className="block w-full text-right font-mono text-lg tabular-nums text-fg">{portfolio.baseCurrency}</span>
      </Tile>
    </Surface>
  )
}

function PositionsTable({
  title,
  captionText,
  positions,
  columns,
  sort,
  onSortChange,
  emptyMessage,
}: {
  title: string
  captionText: string
  positions: ReadonlyArray<Position>
  columns: DataTableColumn<Position>[]
  sort: DataTableSort | null
  onSortChange: (key: string) => void
  emptyMessage: string
}) {
  const headingId = `${title.replace(/\s+/g, '-').toLowerCase()}-heading`
  return (
    <section aria-labelledby={headingId}>
      <h2 id={headingId} className="mb-2 font-mono text-sm font-semibold uppercase tracking-wide text-fg-muted">
        {title}
      </h2>
      {positions.length > 0 ? (
        <DataTable
          caption={captionText}
          columns={columns}
          rows={positions}
          getRowKey={(position) => `${position.ticker}-${position.brokerName}`}
          getRowTestId={(position) => `position-row-${position.ticker}-${position.brokerName}`}
          sort={sort}
          onSortChange={onSortChange}
          rowClassName={(position) => (position.priced ? '' : 'bg-surface-2/60')}
        />
      ) : (
        <EmptyState message={emptyMessage} />
      )}
    </section>
  )
}

export function Posiciones() {
  const { data, isPending, isError, error, refetch } = usePortfolio()
  const [searchParams, setSearchParams] = useSearchParams()

  if (isPending) {
    return <LoadingState rows={6} />
  }

  if (isError) {
    const message = error instanceof Error ? error.message : undefined
    return <ErrorState message={message} onRetry={() => refetch()} />
  }

  if (data.positions.length === 0) {
    return <EmptyState message="No tienes posiciones registradas todavía." />
  }

  const sort = parseSort(searchParams)
  const columns = buildColumns(data.baseCurrency)

  function handleSortChange(key: string) {
    setSearchParams(
      (previous) => {
        const next = new URLSearchParams(previous)
        const isSameColumn = previous.get('sort') === key
        next.set('sort', key)
        next.set('dir', isSameColumn && previous.get('dir') === 'desc' ? 'asc' : 'desc')
        return next
      },
      { replace: true },
    )
  }

  const usdPositions = sortPositions(
    data.positions.filter((position) => position.averageCost.currency === 'USD'),
    sort,
  )
  const copPositions = sortPositions(
    data.positions.filter((position) => position.averageCost.currency === 'COP'),
    sort,
  )

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-mono text-xl font-semibold text-fg">Posiciones</h1>
      <SummaryTiles portfolio={data} />
      {data.unpricedTickers.length > 0 && (
        <p role="note" className="font-sans text-sm text-fg-muted">
          Sin valorar (falta precio o tasa de cambio): {data.unpricedTickers.join(', ')}. Los totales anteriores no
          incluyen el valor de mercado de estas posiciones.
        </p>
      )}
      <PositionsTable
        title="NYSE / NASDAQ · USD"
        captionText="Posiciones en dólares, NYSE y NASDAQ"
        positions={usdPositions}
        columns={columns}
        sort={sort}
        onSortChange={handleSortChange}
        emptyMessage="No hay posiciones en dólares."
      />
      <PositionsTable
        title="Trii / BVC · COP"
        captionText="Posiciones en pesos, Trii y BVC"
        positions={copPositions}
        columns={columns}
        sort={sort}
        onSortChange={handleSortChange}
        emptyMessage="No hay posiciones en pesos."
      />
      <TotalReturnPanel />
    </div>
  )
}
