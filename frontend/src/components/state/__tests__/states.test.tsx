import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EmptyState } from '../EmptyState'
import { ErrorState } from '../ErrorState'
import { LoadingState } from '../LoadingState'

describe('LoadingState', () => {
  it('renders a skeleton, not a spinner', () => {
    render(<LoadingState rows={3} />)
    const skeleton = screen.getByRole('status')
    expect(skeleton).toHaveAttribute('aria-label', expect.stringMatching(/cargando/i))
    // A skeleton is made of placeholder blocks, not a single spinner glyph.
    expect(skeleton.querySelectorAll('[data-skeleton-row]')).toHaveLength(3)
  })

  it('marks every placeholder block as decorative so a screen reader only hears the status label once', () => {
    render(<LoadingState rows={2} />)
    const rows = document.querySelectorAll('[data-skeleton-row]')
    rows.forEach((row) => expect(row).toHaveAttribute('aria-hidden', 'true'))
  })
})

describe('ErrorState', () => {
  it('never renders a bare "something went wrong": it shows the server detail when given one', () => {
    render(<ErrorState message="El servidor no respondió a tiempo." onRetry={() => {}} />)
    expect(screen.getByText('El servidor no respondió a tiempo.')).toBeInTheDocument()
  })

  it('falls back to a generic Spanish message when no server detail is available', () => {
    render(<ErrorState onRetry={() => {}} />)
    expect(screen.getByText(/ocurrió un error/i)).toBeInTheDocument()
  })

  it('provides a retry affordance that calls back when activated', async () => {
    let retried = false
    render(<ErrorState message="falló" onRetry={() => (retried = true)} />)
    const retryButton = screen.getByRole('button', { name: /reintentar/i })
    retryButton.click()
    expect(retried).toBe(true)
  })
})

describe('EmptyState', () => {
  it('is explicit and explanatory, distinguishing an empty result from a failure', () => {
    render(<EmptyState message="No hay transacciones todavía" />)
    expect(screen.getByText('No hay transacciones todavía')).toBeInTheDocument()
  })
})
