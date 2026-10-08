import { useState } from 'react'
import { useWatchlist } from '../api/queries/watchlist'
import { formatDecimalString, formatScore } from '../api/money'
import { compareDecimalStrings } from '../api/decimalCompare'
import { NoDataCell, Tag } from '../design/primitives'
import { DataTable, type DataTableColumn, type DataTableSort } from '../components/table'
import { EmptyState, ErrorState, LoadingState } from '../components/state'
import { FundamentalsPanel } from './Fundamentals'
import type { WatchlistItem } from '../api/schemas/watchlist'

// A dash for a field the backend documents as "frequently absent upstream"
// — absence, not a zero. `NoDataCell` is the one shared definition (see
// design/primitives/NoDataCell.tsx); this view no longer reimplements it.
const DashCell = NoDataCell

function PriceCell({ item }: { item: WatchlistItem }) {
  if (item.price === null) return <DashCell />
  return <span className="block w-full text-right font-mono tabular-nums text-fg">{formatDecimalString(item.price, 2, true)}</span>
}

function TechnicalScoreCell({ item }: { item: WatchlistItem }) {
  if (item.technicalScore === null) return <DashCell />
  return <span className="block w-full text-right font-mono tabular-nums text-fg">{formatScore(item.technicalScore)}</span>
}

// The watchlist's own signal vocabulary — COMPRA / FUERTE COMPRA /
// MANTENER / VENTA, confirmed live, note the space in "FUERTE COMPRA" —
// is rendered verbatim. It is deliberately NOT mapped into the
// BUY/HOLD/STRONG_SELL vocabulary the rest of the contract (e.g. AI
// synthesis) uses: the backend sends two distinct vocabularies for two
// distinct endpoints, and translating one into the other here would be
// the UI inventing a business mapping the API never made.
const SIGNAL_TONE: Record<string, 'positive' | 'negative' | 'accent' | 'neutral'> = {
  'COMPRA': 'positive',
  'FUERTE COMPRA': 'positive',
  'MANTENER': 'accent',
  'VENTA': 'negative',
}

// A null `signal` must read as "no signal computed", never as a blank
// badge, an empty-but-aligned cell, or (worst) a level that implies a
// recommendation the backend never made.
function SignalCell({ item }: { item: WatchlistItem }) {
  if (item.signal === null) return <DashCell />
  return <Tag label={item.signal} tone={SIGNAL_TONE[item.signal] ?? 'neutral'} />
}

// `-1` is a sort-only sentinel, never displayed and never fed to
// `formatScore`/`formatDecimalString` — it exists purely to park a null
// row at one stable end of `compareDecimalStrings`'s ordering (no real
// score is negative), for every nullable numeric column including the
// default sort column (`combinedScore`), which can now carry nulls too.
function sortValueFor(item: WatchlistItem, key: string): string {
  if (key === 'price') return item.price ?? '-1'
  if (key === 'technicalScore') return item.technicalScore ?? '-1'
  if (key === 'wsbScore') return item.wsbScore ?? '-1'
  if (key === 'combinedScore') return item.combinedScore ?? '-1'
  return ''
}

function sortWatchlist(items: ReadonlyArray<WatchlistItem>, sort: DataTableSort | null): WatchlistItem[] {
  if (sort === null) return [...items]
  const sorted = [...items].sort((a, b) => compareDecimalStrings(sortValueFor(a, sort.key), sortValueFor(b, sort.key)))
  return sort.direction === 'desc' ? sorted.reverse() : sorted
}

function buildColumns(onOpenFundamentals: (ticker: string) => void): DataTableColumn<WatchlistItem>[] {
  return [
    {
      key: 'ticker',
      header: 'Ticker',
      align: 'left',
      width: '7rem',
      render: (item) => (
        <div className="flex items-center gap-2">
          <span className="font-medium text-fg">{item.ticker}</span>
          <button
            type="button"
            id={`fundamentales-trigger-${item.ticker}`}
            onClick={() => onOpenFundamentals(item.ticker)}
            aria-label={`Ver fundamentales de ${item.ticker}`}
            className="rounded-sm font-sans text-xs text-accent underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-ground"
          >
            Fundamentales
          </button>
        </div>
      ),
    },
    {
      key: 'price',
      header: 'Precio',
      sortable: true,
      width: '6rem',
      render: (item) => <PriceCell item={item} />,
    },
    {
      key: 'technicalScore',
      header: 'Técnico',
      sortable: true,
      width: '6rem',
      render: (item) => <TechnicalScoreCell item={item} />,
    },
    {
      key: 'wsbScore',
      header: 'WSB',
      sortable: true,
      width: '6rem',
      render: (item) =>
        item.wsbScore === null ? (
          <DashCell />
        ) : (
          <span className="block w-full text-right font-mono tabular-nums text-fg">{formatScore(item.wsbScore)}</span>
        ),
    },
    {
      key: 'combinedScore',
      header: 'Combinado',
      sortable: true,
      width: '6.5rem',
      render: (item) =>
        item.combinedScore === null ? (
          <DashCell />
        ) : (
          <span className="block w-full text-right font-mono tabular-nums text-fg">
            {formatScore(item.combinedScore)}
          </span>
        ),
    },
    {
      key: 'signal',
      header: 'Señal',
      width: '8rem',
      render: (item) => <SignalCell item={item} />,
    },
  ]
}

export function Watchlist() {
  const { data, isPending, isError, error, refetch } = useWatchlist()
  const [openedTicker, setOpenedTicker] = useState<string | null>(null)
  const [sort, setSort] = useState<DataTableSort | null>({ key: 'combinedScore', direction: 'desc' })

  if (isPending) {
    return <LoadingState rows={8} />
  }

  if (isError) {
    const message = error instanceof Error ? error.message : undefined
    return <ErrorState message={message} onRetry={() => refetch()} />
  }

  if (data.length === 0) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="font-mono text-xl font-semibold text-fg">Watchlist</h1>
        <EmptyState message="No hay tickers en la watchlist." />
      </div>
    )
  }

  const rows = sortWatchlist(data, sort)

  function handleSortChange(key: string) {
    setSort((previous) => {
      const isSameColumn = previous?.key === key
      return { key, direction: isSameColumn && previous?.direction === 'desc' ? 'asc' : 'desc' }
    })
  }

  // Looked up fresh by id rather than carried in a ref across renders —
  // a captured DOM-node ref would go stale the moment this table
  // re-sorts. Important 7: returns focus to the trigger on close
  // instead of dropping it to <body>.
  function handleCloseFundamentals() {
    const ticker = openedTicker
    setOpenedTicker(null)
    if (ticker !== null) {
      document.getElementById(`fundamentales-trigger-${ticker}`)?.focus()
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-mono text-xl font-semibold text-fg">Watchlist</h1>
      <DataTable
        caption="Watchlist: precio, puntajes técnico y WSB, puntaje combinado y señal"
        columns={buildColumns((ticker) => setOpenedTicker(ticker))}
        rows={rows}
        getRowKey={(item) => item.ticker}
        getRowTestId={(item) => `watchlist-row-${item.ticker}`}
        sort={sort}
        onSortChange={handleSortChange}
      />
      {openedTicker !== null && <FundamentalsPanel ticker={openedTicker} onClose={handleCloseFundamentals} />}
    </div>
  )
}
