import type { Money } from './schemas/common'

export type { Money }

export interface FormatMoneyOptions {
  /** Number of fraction digits to show. Default 2. Rounds half away from zero. */
  decimals?: number
  /** Whether to prefix the currency code. Default true. */
  withCurrency?: boolean
}

export interface FormatDecimalOptions {
  /** Number of fraction digits to show. Rounds half away from zero. */
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

// Lookup table for incrementing a single decimal digit, with the carry
// flag implicit in whether the input was '9'. A table avoids any
// arithmetic operator on the digit's value.
const DIGIT_INCREMENT: Record<string, string> = {
  '0': '1',
  '1': '2',
  '2': '3',
  '3': '4',
  '4': '5',
  '5': '6',
  '6': '7',
  '7': '8',
  '8': '9',
  '9': '0',
}

/**
 * Increments a non-negative digit string by 1, propagating the carry
 * leftward through any trailing 9s, and growing the string by one digit
 * if the carry overflows past the leftmost position (e.g. "99" -> "100").
 * Pure digit-table lookup and string concatenation — no `Number()`, no
 * arithmetic operator, ever touches the digits.
 */
function incrementDigits(digits: string): string {
  const chars = digits.split('')
  for (let i = chars.length - 1; i >= 0; i -= 1) {
    const current = chars[i]
    if (current === undefined) break
    chars[i] = DIGIT_INCREMENT[current] ?? '0'
    if (current !== '9') {
      return chars.join('')
    }
  }
  // Every kept digit was a '9': the carry overflowed past the leftmost
  // position, so the digit string grows by one place.
  return `1${chars.join('')}`
}

interface RoundedParts {
  integerDigits: string
  fractionDigits: string
}

/**
 * Rounds a decimal string's unsigned integer/fraction digit parts to an
 * exact fraction width, half away from zero — the convention a reader
 * expects ("2.5" at 0 decimals renders "3", not "2"). Looking at only the
 * single digit immediately after the kept precision is sufficient to
 * decide round-up-or-down for an exact decimal string: any further digits
 * only matter when that one digit is itself exactly the boundary, and
 * "round half away from zero" already rounds that boundary up regardless
 * of what follows. No `Number()`, no `parseFloat`, no `*`, no `/` — only
 * digit-table increments and string slicing.
 */
function roundDecimalParts(integerPart: string, fractionPart: string, decimals: number): RoundedParts {
  const normalizedInteger = stripLeadingZeros(integerPart.length > 0 ? integerPart : '0')

  if (fractionPart.length <= decimals) {
    return { integerDigits: normalizedInteger, fractionDigits: fractionPart.padEnd(decimals, '0') }
  }

  const keptFraction = fractionPart.slice(0, decimals)
  const roundingDigit = fractionPart[decimals] ?? '0'
  const roundUp = roundingDigit >= '5'

  if (!roundUp) {
    return { integerDigits: normalizedInteger, fractionDigits: keptFraction }
  }

  if (decimals === 0) {
    return { integerDigits: incrementDigits(normalizedInteger), fractionDigits: '' }
  }

  const incrementedFraction = incrementDigits(keptFraction)
  if (incrementedFraction.length > keptFraction.length) {
    // The carry propagated out of every kept fraction digit into the
    // integer part (e.g. keptFraction "99" -> "100"): keep only the last
    // `decimals` digits of the increment as the fraction, and carry the
    // overflow into the integer part too.
    const carriedFraction = incrementedFraction.slice(incrementedFraction.length - decimals)
    return { integerDigits: incrementDigits(normalizedInteger), fractionDigits: carriedFraction }
  }

  return { integerDigits: normalizedInteger, fractionDigits: incrementedFraction }
}

/**
 * Formats a decimal string with thousands grouping and a fixed fraction
 * width, rounding half away from zero through string/digit-table
 * operations only. No `Number(`, `parseFloat`, `parseInt`, unary `+`,
 * `*` or `/` ever touches the value — see money-source.test.ts, which
 * reads this module's own source to enforce that.
 */
function formatDecimalString(value: string, decimals: number, group: boolean): string {
  const { sign, digits } = splitSign(value)
  const dotIndex = digits.indexOf('.')
  const integerPart = dotIndex === -1 ? digits : digits.slice(0, dotIndex)
  const fractionPart = dotIndex === -1 ? '' : digits.slice(dotIndex + 1)

  const { integerDigits, fractionDigits } = roundDecimalParts(integerPart, fractionPart, decimals)
  const groupedInteger = group ? groupThousands(integerDigits) : integerDigits

  const body = decimals > 0 ? `${groupedInteger}.${fractionDigits}` : groupedInteger
  return `${sign}${body}`
}

/**
 * Formats a Money value as a string. Never calls `Number()` or
 * `parseFloat` on the amount, and never rounds through a float — the
 * rounding itself is done by incrementing decimal digit characters via a
 * lookup table, with explicit carry propagation, so the backend's string
 * precision is the only input.
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
