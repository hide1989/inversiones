import type { FormattedValue } from './Figure'

export type DeltaSign = 'positive' | 'negative' | 'zero'

export interface DeltaProps {
  /** The unsigned magnitude, already formatted (e.g. "97.80"). */
  value: FormattedValue
  /** Direction of the change, decided upstream from the exact decimal. */
  sign: DeltaSign
  /**
   * The value's currency code, shown alongside it when a column mixes
   * currencies (e.g. a USD segment next to a COP segment) and the value
   * would otherwise be unreadable without it. Optional and additive —
   * every existing single-currency caller is unaffected.
   */
  currency?: string
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
 * fails colour-blind readers and a greyscale print. When `currency` is
 * given, it renders with the value — the same rule `formatMoney` follows
 * for every other figure — so a cell stays self-describing even when
 * read in isolation (a reordered table, a screen reader stepping column
 * by column).
 */
export function Delta({ value, sign, currency, className = '' }: DeltaProps) {
  const { symbol, arrow, colorClass } = SIGN_CONFIG[sign]
  return (
    <span
      className={`inline-flex w-full items-center justify-end gap-1 font-mono tabular-nums ${colorClass} ${className}`.trim()}
    >
      {arrow !== '' && <span aria-hidden="true">{arrow}</span>}
      <span>
        {currency !== undefined && `${currency} `}
        {symbol}
        {value}
      </span>
    </span>
  )
}
