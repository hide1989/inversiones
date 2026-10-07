export interface EmptyStateProps {
  /** Explicit, explanatory Spanish copy — e.g. "No hay transacciones todavía". */
  message: string
  className?: string
}

/**
 * An explicit empty state: a successful request with no rows is the truth
 * in this application more often than it is a failure, so it says so
 * rather than leaving a blank table that reads as broken.
 */
export function EmptyState({ message, className = '' }: EmptyStateProps) {
  return (
    <div
      className={`flex items-center justify-center rounded-md border border-dashed border-border p-6 font-sans text-sm text-fg-subtle ${className}`.trim()}
    >
      {message}
    </div>
  )
}
