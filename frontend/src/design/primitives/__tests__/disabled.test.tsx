import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Button } from '../Button'

describe('disabled primitives', () => {
  it('a disabled Button is not focusable and carries aria-disabled', async () => {
    const user = userEvent.setup()
    render(
      <>
        <Button disabled>Deshabilitado</Button>
        <input type="text" aria-label="after" />
      </>,
    )

    const button = screen.getByRole('button', { name: 'Deshabilitado' })
    expect(button).toHaveAttribute('aria-disabled', 'true')

    await user.tab()
    expect(button).not.toHaveFocus()
  })
})
