import { Button } from '../../design/primitives'

const GENERIC_MESSAGE = 'Ocurrió un error al cargar los datos.'

export interface ErrorStateProps {
  /** The server's own message (e.g. a ProblemDetail's `detail`), shown verbatim when there is one. */
  message?: string
  onRetry: () => void
  className?: string
}

/**
 * Actionable, never a blank screen and never a bare "something went
 * wrong": it always shows a message — the server's own detail when the
 * caller has one, a generic Spanish fallback otherwise — paired with a
 * retry affordance. `role="alert"` so assistive technology announces it
 * without the view having to repeat that wiring.
 */
export function ErrorState({ message, onRetry, className = '' }: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={`flex flex-col items-start gap-3 rounded-md border border-negative/40 bg-negative/10 p-4 ${className}`.trim()}
    >
      <p className="font-sans text-sm text-fg">{message ?? GENERIC_MESSAGE}</p>
      <Button variant="ghost" onClick={onRetry}>
        Reintentar
      </Button>
    </div>
  )
}
