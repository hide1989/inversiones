export type TagTone = 'neutral' | 'accent' | 'positive' | 'negative'

export interface TagProps {
  /** The label itself carries the meaning — colour is supplementary, never the only signal. */
  label: string
  tone?: TagTone
  className?: string
}

const TONE_CLASS: Record<TagTone, string> = {
  neutral: 'text-fg-muted',
  accent: 'text-accent',
  positive: 'text-positive',
  negative: 'text-negative',
}

/** A signal level or broker name. */
export function Tag({ label, tone = 'neutral', className = '' }: TagProps) {
  return (
    <span
      className={`inline-flex items-center rounded-sm bg-surface-3 px-1.5 py-0.5 font-mono text-xs font-medium ${TONE_CLASS[tone]} ${className}`.trim()}
    >
      {label}
    </span>
  )
}
