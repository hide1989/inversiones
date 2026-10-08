export interface NoDataCellProps {
  /** The screen-reader text announced in place of the hidden em-dash. Defaults to "Sin dato". */
  label?: string
  className?: string
}

/**
 * The em-dash shown for a field the backend documents as absent — never a zero, never a blank
 * cell. The dash itself is `aria-hidden`, because a bare "—" announces as "em dash" or is skipped
 * entirely depending on the screen reader, neither of which says anything useful; the paired
 * `sr-only` span is the actual accessible name. A `<td>` with nothing in it is otherwise silent
 * to a screen reader and indistinguishable from a rendering bug, which is what this primitive
 * exists to prevent. One definition shared by every table that has nullable cells (Watchlist,
 * Posiciones, Senales) rather than three independent reimplementations of the same dash.
 */
export function NoDataCell({ label = 'Sin dato', className = 'text-fg-subtle' }: NoDataCellProps) {
  return (
    <>
      <span aria-hidden="true" className={className}>
        —
      </span>
      <span className="sr-only">{label}</span>
    </>
  )
}
