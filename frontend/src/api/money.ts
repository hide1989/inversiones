import type { Money } from './schemas/common'

export type { Money }

export interface FormatMoneyOptions {
  /** Number of fraction digits to show. Truncates or zero-pads the decimal string. Default 2. */
  decimals?: number
  /** Whether to prefix the currency code. Default true. */
  withCurrency?: boolean
}

export interface FormatDecimalOptions {
  /** Number of fraction digits to show. Truncates or zero-pads the decimal string. */
  decimals?: number
}

/**
 * Splits a sign off a decimal-string value.
 * Pure string slicing — no numeric coercion.
 */
function splitSign(value: string): { sign: '' | '-'; digits: string } {
  if (value.startsWith('-')) {
    return { sign: '-', digits: value.slice(1) }
  }
  return { sign: '', digits: value }
}

/**
 * Groups the integer part of a decimal string with thousands separators.
 * Works right-to-left over the digit string; never touches a number type.
 */
function groupThousands(integerDigits: string): string {
  let result = ''
  for (let i = 0; i < integerDigits.length; i += 1) {
    const fromEnd = integerDigits.length - i
    if (i > 0 && fromEnd % 3 === 0) {
      result += ','
    }
    result += integerDigits[i]
  }
  return result
}

/**
 * Strips leading zeros from an integer digit string, keeping at least one digit.
 */
function stripLeadingZeros(integerDigits: string): string {
  const stripped = integerDigits.replace(/^0+(?=\d)/, '')
  return stripped.length > 0 ? stripped : '0'
}

/**
 * Pads or truncates a fraction digit string to an exact length.
 * Truncation here is display-only and never feeds back into further
 * arithmetic — the backend's own value remains the source of truth.
 */
function fitFraction(fractionDigits: string, decimals: number): string {
  if (decimals <= 0) return ''
  if (fractionDigits.length >= decimals) {
    return fractionDigits.slice(0, decimals)
  }
  return fractionDigits.padEnd(decimals, '0')
}

/**
 * Formats a decimal string with thousands grouping and a fixed fraction
 * width, entirely through string operations. No `Number(`, `parseFloat`,
 * or unary `+` ever touches the value — see money-source.test.ts, which
 * reads this module's own source to enforce that.
 */
function formatDecimalString(value: string, decimals: number, group: boolean): string {
  const { sign, digits } = splitSign(value)
  const dotIndex = digits.indexOf('.')
  const integerPart = dotIndex === -1 ? digits : digits.slice(0, dotIndex)
  const fractionPart = dotIndex === -1 ? '' : digits.slice(dotIndex + 1)

  const normalizedInteger = stripLeadingZeros(integerPart.length > 0 ? integerPart : '0')
  const groupedInteger = group ? groupThousands(normalizedInteger) : normalizedInteger
  const fraction = fitFraction(fractionPart, decimals)

  const body = decimals > 0 ? `${groupedInteger}.${fraction}` : groupedInteger
  return `${sign}${body}`
}

/**
 * Formats a Money value as a string. Never calls `Number()` or
 * `parseFloat` on the amount, and never rounds through a float —
 * the backend already computed every total, subtotal and percentage;
 * this function only renders the string it returned.
 */
export function formatMoney(money: Money, options: FormatMoneyOptions = {}): string {
  const decimals = options.decimals ?? 2
  const withCurrency = options.withCurrency ?? true
  const formattedNumber = formatDecimalString(money.amount, decimals, true)
  return withCurrency ? `${money.currency} ${formattedNumber}` : formattedNumber
}

/**
 * Formats a percentage string (e.g. `unrealizedPnlPercent`). The value
 * already is the percentage; this only fixes the decimal width and
 * appends the sign. No numeric coercion.
 */
export function formatPercent(value: string, options: FormatDecimalOptions = {}): string {
  const decimals = options.decimals ?? 2
  return `${formatDecimalString(value, decimals, false)}%`
}

/**
 * Formats a score string (e.g. a CombinedScoreDto's `value`). Fixes the
 * decimal width only — the backend computed the score.
 */
export function formatScore(value: string, options: FormatDecimalOptions = {}): string {
  const decimals = options.decimals ?? 1
  return formatDecimalString(value, decimals, false)
}
