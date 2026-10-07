import type { ReactNode } from 'react'

export type SortDirection = 'asc' | 'desc'

export interface DataTableSort {
  key: string
  direction: SortDirection
}

export interface DataTableColumn<T> {
  key: string
  header: string
  /** Numbers right-align, tickers left-align, nothing centres — per the design direction. Defaults to right. */
  align?: 'left' | 'right'
  sortable?: boolean
  render: (row: T) => ReactNode
}

export interface DataTableProps<T> {
  /** The table's accessible name, visually hidden as a `<caption>`. Every table needs one — Task 12's axe audit checks for it. */
  caption: string
  columns: ReadonlyArray<DataTableColumn<T>>
  rows: ReadonlyArray<T>
  getRowKey: (row: T) => string
  sort?: DataTableSort | null
  /** Called with a column's key when its header is activated; the caller owns toggling direction and persisting it (Positions keeps it in the URL). */
  onSortChange?: (key: string) => void
  /** Task 8's fifteen-column signals table extends the row-height token with this tighter variant rather than inventing its own. */
  density?: 'standard' | 'dense'
  rowClassName?: (row: T) => string
  /** Optional `data-testid` per row, for tests that need to assert row order or identity directly. */
  getRowTestId?: (row: T) => string
}

type SortState = 'ascending' | 'descending' | 'none'

function sortStateFor<T>(column: DataTableColumn<T>, sort: DataTableSort | null | undefined): SortState {
  if (!column.sortable || !sort || sort.key !== column.key) return 'none'
  return sort.direction === 'asc' ? 'ascending' : 'descending'
}

function SortGlyph({ state }: { state: SortState }) {
  if (state === 'none') {
    return (
      <span aria-hidden="true" className="text-fg-subtle">
        ⇅
      </span>
    )
  }
  return <span aria-hidden="true">{state === 'ascending' ? '▲' : '▼'}</span>
}

/**
 * The shared table primitive for every dense data view: Positions here,
 * and the fifteen-column Signals table in Task 8, which must reuse this
 * component and the `--color-table-*` / `--spacing-row-y*` tokens in
 * tokens.css rather than inventing its own header, padding or zebra
 * treatment. Sorting state and comparison are the caller's job — this
 * component only renders the current state and reports activation.
 */
export function DataTable<T>({
  caption,
  columns,
  rows,
  getRowKey,
  sort = null,
  onSortChange,
  density = 'standard',
  rowClassName,
  getRowTestId,
}: DataTableProps<T>) {
  const cellY = density === 'dense' ? 'py-row-y-dense' : 'py-row-y'

  return (
    <div className="overflow-x-auto rounded-md">
      <table className="w-full border-collapse font-mono text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="bg-table-header">
            {columns.map((column) => {
              const state = sortStateFor(column, sort)
              const isLeft = column.align === 'left'
              return (
                <th
                  key={column.key}
                  scope="col"
                  aria-sort={column.sortable ? state : undefined}
                  className={`px-cell-x ${cellY} text-xs font-medium uppercase tracking-wide text-fg-muted ${isLeft ? 'text-left' : 'text-right'}`}
                >
                  {column.sortable ? (
                    <button
                      type="button"
                      onClick={() => onSortChange?.(column.key)}
                      className={`inline-flex items-center gap-1 rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-ground ${isLeft ? '' : 'w-full justify-end'}`}
                    >
                      {column.header}
                      <SortGlyph state={state} />
                    </button>
                  ) : (
                    column.header
                  )}
                </th>
              )
            })}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr
              key={getRowKey(row)}
              data-testid={getRowTestId?.(row)}
              className={`${index % 2 === 1 ? 'bg-table-row-stripe' : ''} hover:bg-table-row-hover ${rowClassName?.(row) ?? ''}`.trim()}
            >
              {columns.map((column) => (
                <td
                  key={column.key}
                  className={`px-cell-x ${cellY} ${column.align === 'left' ? 'text-left' : 'text-right'}`}
                >
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
