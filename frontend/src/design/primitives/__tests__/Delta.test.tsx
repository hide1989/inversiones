import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Delta } from '../Delta'

describe('Delta', () => {
  it('renders the sign and arrow for a positive value', () => {
    const { container } = render(<Delta value="97.80" sign="positive" />)
    expect(container.textContent).toContain('▲')
    expect(container.textContent).toContain('+97.80')
  })

  it('renders the sign and arrow for a negative value', () => {
    const { container } = render(<Delta value="12.40" sign="negative" />)
    expect(container.textContent).toContain('▼')
    expect(container.textContent).toContain('−12.40')
  })

  it('renders a zero value with neither arrow', () => {
    const { container } = render(<Delta value="0.00" sign="zero" />)
    expect(container.textContent).not.toContain('▲')
    expect(container.textContent).not.toContain('▼')
    expect(container.textContent).toContain('0.00')
  })
})
