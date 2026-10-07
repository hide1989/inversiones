import { describe, expect, it } from 'vitest'
import { formatMoney, formatPercent, formatScore } from '../money'

describe('formatMoney', () => {
  it('preserves every significant digit of a scale-10 amount', () => {
    const result = formatMoney({ amount: '300.0000000000', currency: 'USD' })
    expect(result).toBe('USD 300.00')
    expect(result).not.toContain('e')
    expect(result).not.toContain('E')
  })

  it('preserves precision for a large amount without switching to exponent notation', () => {
    const result = formatMoney({ amount: '1234567.8900000000', currency: 'USD' }, { decimals: 2 })
    // A float would render this as 1234567.89 OR, pushed further, as
    // 1.2345678899999999e6. Assert the exact grouped string so either
    // failure mode would be caught.
    expect(result).toBe('USD 1,234,567.89')
    expect(result).not.toMatch(/e\+?\d/i)
  })

  it('never rounds the fraction, only truncates or pads it', () => {
    // 0.005 rounds to 0.01 under float rounding; truncation keeps it 0.00.
    expect(formatMoney({ amount: '10.005', currency: 'USD' }, { decimals: 2 })).toBe('USD 10.00')
    // Padding a short fraction does not invent precision.
    expect(formatMoney({ amount: '10.5', currency: 'USD' }, { decimals: 4 })).toBe('USD 10.5000')
  })

  it('preserves the sign on a negative amount', () => {
    expect(formatMoney({ amount: '-328.4200000000', currency: 'USD' })).toBe('USD -328.42')
  })

  it('can omit the currency prefix', () => {
    expect(formatMoney({ amount: '2463.00', currency: 'USD' }, { withCurrency: false })).toBe('2,463.00')
  })

  it('groups a very large integer part correctly', () => {
    expect(formatMoney({ amount: '9582784.0000000000', currency: 'COP' })).toBe('COP 9,582,784.00')
  })
})

describe('formatPercent', () => {
  it('renders a decimal string with a percent sign, no rounding through a float', () => {
    expect(formatPercent('22.4800000000')).toBe('22.48%')
    expect(formatPercent('-0.8200000000')).toBe('-0.82%')
  })
})

describe('formatScore', () => {
  it('renders a decimal string fixed to one fraction digit by default', () => {
    expect(formatScore('8.2000000000')).toBe('8.2')
    expect(formatScore('8')).toBe('8.0')
  })
})
