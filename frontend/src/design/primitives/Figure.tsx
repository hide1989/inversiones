/**
 * A value that is already formatted for display (e.g. produced by the
 * Money type in Task 3). It is `string`, never `number`: a raw JS number
 * flowing into a figure would mean precision was already lost upstream,
 * since the API transports amounts as strings for exactly that reason.
 */
export type FormattedValue = string

export interface FigureProps {
  /** The exact text to display. Rendered unchanged — no reformatting, no rounding. */
  value: FormattedValue
  className?: string
}

/**
 * A numeric value, right-aligned with tabular figures so a changing digit
 * never reflows the row. Accepts only a formatted string: passing a
 * `number` is a compile error, not a runtime concern.
 */
export function Figure({ value, className = '' }: FigureProps) {
  return (
    <span className={`block w-full font-mono tabular-nums text-right text-fg ${className}`.trim()}>
      {value}
    </span>
  )
}
