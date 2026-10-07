/**
 * Orders two decimal-string amounts (a `Money.amount`, a quantity, a
 * percent) for a sortable table column, without ever parsing either one
 * into a JavaScript number. Used by Positions (Task 6) and intended for
 * Signals' numeric columns (Task 8) too — a comparator, not arithmetic,
 * so it does not fall under money.ts's no-float-coercion scan, but it
 * still never calls `Number()` or `parseFloat` on the value.
 */

interface DecimalParts {
  negative: boolean
  integerDigits: string
  fractionDigits: string
}

function parseDecimal(value: string): DecimalParts {
  const trimmed = value.trim()
  const negative = trimmed.startsWith('-')
  const unsigned = negative || trimmed.startsWith('+') ? trimmed.slice(1) : trimmed

  const dotIndex = unsigned.indexOf('.')
  const rawInteger = dotIndex === -1 ? unsigned : unsigned.slice(0, dotIndex)
  const fractionDigits = dotIndex === -1 ? '' : unsigned.slice(dotIndex + 1)

  let start = 0
  while (start < rawInteger.length - 1 && rawInteger[start] === '0') {
    start += 1
  }
  const integerDigits = rawInteger.length > 0 ? rawInteger.slice(start) : '0'

  return { negative, integerDigits, fractionDigits }
}

function isZero(parts: DecimalParts): boolean {
  if (parts.integerDigits !== '0') return false
  for (const digit of parts.fractionDigits) {
    if (digit !== '0') return false
  }
  return true
}

/** Compares two unsigned magnitudes: longer integer part wins; equal-length integers compare lexicographically; fraction parts compare after padding to equal width. */
function compareMagnitude(a: DecimalParts, b: DecimalParts): number {
  if (a.integerDigits.length !== b.integerDigits.length) {
    return a.integerDigits.length < b.integerDigits.length ? -1 : 1
  }
  if (a.integerDigits !== b.integerDigits) {
    return a.integerDigits < b.integerDigits ? -1 : 1
  }

  const width = a.fractionDigits.length > b.fractionDigits.length ? a.fractionDigits.length : b.fractionDigits.length
  const fractionA = a.fractionDigits.padEnd(width, '0')
  const fractionB = b.fractionDigits.padEnd(width, '0')
  if (fractionA === fractionB) return 0
  return fractionA < fractionB ? -1 : 1
}

/**
 * Returns a negative number if `a` orders before `b`, positive if after,
 * zero if equal — suitable for `Array.prototype.sort`. Handles sign and
 * leading zeros through string/digit comparison only.
 */
export function compareDecimalStrings(a: string, b: string): number {
  const partsA = parseDecimal(a)
  const partsB = parseDecimal(b)
  const zeroA = isZero(partsA)
  const zeroB = isZero(partsB)

  if (zeroA && zeroB) return 0
  if (zeroA) return partsB.negative ? 1 : -1
  if (zeroB) return partsA.negative ? -1 : 1

  if (partsA.negative !== partsB.negative) {
    return partsA.negative ? -1 : 1
  }

  const magnitudeComparison = compareMagnitude(partsA, partsB)
  return partsA.negative ? -magnitudeComparison : magnitudeComparison
}
