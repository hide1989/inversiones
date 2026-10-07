import type { FormattedValue } from './Figure'

export type DeltaSign = 'positive' | 'negative' | 'zero'

export interface DeltaProps {
  /** The unsigned magnitude, already formatted (e.g. "97.80"). */
  value: FormattedValue
  /** Direction of the change, decided upstream from the exact decimal. */
  sign: DeltaSign
  className?: string
}

const SIGN_CONFIG: Record<DeltaSign, { symbol: string; arrow: string; colorClass: string }> = {
  positive: { symbol: '+', arrow: '▲', colorClass: 'text-positive' },
  negative: { symbol: '−', arrow: '▼', colorClass: 'text-negative' },
  zero: { symbol: '', arrow: '', colorClass: 'text-fg-muted' },
}

/**
 * A signed change: colour, an explicit `+`/`−`, and a `▲`/`▼` arrow, always
 * together. None of the three is individually optional — colour alone
 * fails colour-blind readers and a greyscale print.
 */
export function Delta({ value, sign, className = '' }: DeltaProps) {
  const { symbol, arrow, colorClass } = SIGN_CONFIG[sign]
  return (
    <span
      className={`inline-flex w-full items-center justify-end gap-1 font-mono tabular-nums ${colorClass} ${className}`.trim()}
    >
      {arrow !== '' && <span aria-hidden="true">{arrow}</span>}
      <span>
        {symbol}
        {value}
      </span>
    </span>
  )
}
