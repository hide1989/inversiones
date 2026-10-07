import type { DeltaSign } from '../design/primitives'

/**
 * Strips a leading sign character from a decimal string, leaving the
 * unsigned magnitude `Delta` expects — string slicing only, no numeric
 * coercion.
 */
export function magnitudeOf(value: string): string {
  const trimmed = value.trim()
  if (trimmed.startsWith('-') || trimmed.startsWith('+')) {
    return trimmed.slice(1)
  }
  return trimmed
}

/**
 * Decides a `Delta`'s sign from a decimal string's own text: a leading
 * `-` is negative, an all-zero magnitude is zero regardless of sign
 * character, anything else is positive. Never parses the string into a
 * number to make the decision.
 */
export function signOf(value: string): DeltaSign {
  const trimmed = value.trim()
  const unsigned = magnitudeOf(trimmed)

  let allZero = true
  for (const char of unsigned) {
    if (char !== '0' && char !== '.') {
      allZero = false
      break
    }
  }
  if (allZero) return 'zero'

  return trimmed.startsWith('-') ? 'negative' : 'positive'
}
