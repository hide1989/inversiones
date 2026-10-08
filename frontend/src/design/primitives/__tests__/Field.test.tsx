import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Field } from '../Field'

// Final review Important 4: before this fix, a validation error was
// passed as `hint`, which never sets `aria-invalid`, has no `role`, and
// renders in the identical `text-fg-subtle` grey used for a genuine
// hint like "Opcional" — a screen-reader user hears nothing, and a
// sighted user reads an error as help text.
describe('Field validation errors', () => {
  it('marks the input aria-invalid and announces the error via role="alert", distinct from a hint', () => {
    render(<Field label="Cantidad" value="" onChange={() => {}} invalid="La cantidad es obligatoria." />)

    const input = screen.getByLabelText('Cantidad')
    expect(input).toHaveAttribute('aria-invalid', 'true')

    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('La cantidad es obligatoria.')
    expect(alert.className).toContain('text-negative')
    expect(alert.className).not.toContain('text-fg-subtle')

    expect(input).toHaveAttribute('aria-describedby', alert.id)
  })

  it('a field with no error is not aria-invalid and renders no alert', () => {
    render(<Field label="Cantidad" value="" onChange={() => {}} />)

    const input = screen.getByLabelText('Cantidad')
    expect(input).toHaveAttribute('aria-invalid', 'false')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('keeps a genuine hint and an error visually and semantically distinct when both are present', () => {
    render(
      <Field
        label="Comisiones"
        value=""
        onChange={() => {}}
        hint="Opcional"
        invalid="Las comisiones deben ser un número decimal positivo."
      />,
    )

    const hint = screen.getByText('Opcional')
    expect(hint.className).toContain('text-fg-subtle')

    const alert = screen.getByRole('alert')
    expect(alert.className).toContain('text-negative')
    expect(alert).toHaveTextContent('positivo')

    const input = screen.getByLabelText('Comisiones')
    expect(input.getAttribute('aria-describedby')).toContain(hint.id)
    expect(input.getAttribute('aria-describedby')).toContain(alert.id)
  })
})
