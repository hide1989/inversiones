import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from '../ErrorBoundary'

function Thrower(): never {
  throw new Error('render exploded')
}

describe('ErrorBoundary', () => {
  it('renders the shared error state with a retry affordance instead of a blank screen', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(
      <ErrorBoundary>
        <Thrower />
      </ErrorBoundary>,
    )
    expect(screen.getByRole('button', { name: /reintentar/i })).toBeInTheDocument()
    expect(document.body.textContent).not.toBe('')
    consoleError.mockRestore()
  })

  it('resets its captured error and re-renders children when retry is activated', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const user = userEvent.setup()
    let shouldThrow = true
    function MaybeThrows() {
      if (shouldThrow) {
        throw new Error('render exploded')
      }
      return <div>Recovered content</div>
    }

    render(
      <ErrorBoundary>
        <MaybeThrows />
      </ErrorBoundary>,
    )

    shouldThrow = false
    await user.click(screen.getByRole('button', { name: /reintentar/i }))

    expect(await screen.findByText('Recovered content')).toBeInTheDocument()
    consoleError.mockRestore()
  })
})
