import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Figure } from '../Figure'

describe('Figure', () => {
  it('applies tabular-nums and renders the string unchanged', () => {
    render(<Figure value="300.0000000000" />)
    const node = screen.getByText('300.0000000000')
    expect(node).toHaveClass('tabular-nums')
    expect(node.textContent).toBe('300.0000000000')
  })

  it('rejects a number at compile time', () => {
    // @ts-expect-error Figure accepts only a pre-formatted string, never a number.
    render(<Figure value={300} />)
  })
})
