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

  it('pads a fraction shorter than the requested width rather than rounding', () => {
    expect(formatMoney({ amount: '10.5', currency: 'USD' }, { decimals: 4 })).toBe('USD 10.5000')
  })

  it('rounds half away from zero on a simple case', () => {
    expect(formatMoney({ amount: '10.005', currency: 'USD' }, { decimals: 2 })).toBe('USD 10.01')
  })

  it('rounds a negative amount away from zero (toward a larger-magnitude loss), not toward zero', () => {
    // Truncating toward zero would understate the loss as -12.40; the
    // faithful rounded figure is -12.41.
    expect(formatMoney({ amount: '-12.406', currency: 'USD' }, { decimals: 2 })).toBe('USD -12.41')
  })

  it('propagates a carry through several trailing nines', () => {
    expect(formatMoney({ amount: '1.9995', currency: 'USD' }, { decimals: 3 })).toBe('USD 2.000')
  })

  it('propagates a carry that grows the integer part', () => {
    expect(formatMoney({ amount: '9.99', currency: 'USD' }, { decimals: 1 })).toBe('USD 10.0')
  })

  it('rounds an exact half away from zero for both a positive and a negative amount', () => {
    expect(formatMoney({ amount: '2.5', currency: 'USD' }, { decimals: 0 })).toBe('USD 3')
    expect(formatMoney({ amount: '-2.5', currency: 'USD' }, { decimals: 0 })).toBe('USD -3')
  })

  it('preserves the sign on a negative amount with no rounding needed', () => {
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
