export interface LoadingStateProps {
  /** How many placeholder rows to show — big enough to suggest "a table is coming", not a single bar. */
  rows?: number
  className?: string
}

/**
 * A skeleton, not a spinner: it suggests the shape of what is coming
 * instead of just signalling "something is happening", which reads as
 * less jarring in a dense data view. The rows are decorative (`aria-hidden`)
 * and the single `status` role carries the one announcement a screen
 * reader needs. The shimmer itself is pure CSS (`.skeleton-row` in
 * index.css, driven by the `--color-skeleton-*` / `--motion-shimmer-duration`
 * tokens) and is removed under `prefers-reduced-motion: reduce`.
 */
export function LoadingState({ rows = 4, className = '' }: LoadingStateProps) {
  return (
    <div role="status" aria-label="Cargando" aria-live="polite" className={`flex flex-col gap-2 ${className}`.trim()}>
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} data-skeleton-row aria-hidden="true" className="skeleton-row h-8 w-full rounded-sm" />
      ))}
    </div>
  )
}
