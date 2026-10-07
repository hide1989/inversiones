export interface ScoreBarProps {
  /** The score, as text (e.g. "8.2"). Rendered as real text, never only bar width. */
  score: string
  max?: number
  className?: string
}

/**
 * A 1-10 score as a short inline bar plus the number. The bar is a glance
 * comparator down a column; the number is the thing a screen reader and a
 * careful reader actually rely on.
 */
export function ScoreBar({ score, max = 10, className = '' }: ScoreBarProps) {
  const numeric = Number(score)
  const clamped = Number.isFinite(numeric) ? Math.min(Math.max(numeric, 0), max) : 0
  const percent = (clamped / max) * 100

  return (
    <span className={`inline-flex items-center gap-2 font-mono tabular-nums text-fg ${className}`.trim()}>
      <span
        className="relative inline-block h-1.5 w-10 overflow-hidden rounded-sm bg-surface-3"
        aria-hidden="true"
      >
        <span className="absolute inset-y-0 left-0 bg-accent" style={{ width: `${percent}%` }} />
      </span>
      <span>{score}</span>
    </span>
  )
}
