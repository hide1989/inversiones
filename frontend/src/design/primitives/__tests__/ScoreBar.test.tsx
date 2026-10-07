import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ScoreBar } from '../ScoreBar'

describe('ScoreBar', () => {
  it('renders the numeric score as accessible text', () => {
    render(<ScoreBar score="8.2" />)
    expect(screen.getByText('8.2')).toBeInTheDocument()
  })
})
