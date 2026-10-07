import type { ButtonHTMLAttributes } from 'react'

export type ButtonVariant = 'primary' | 'ghost'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
}

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-accent-fg hover:bg-accent/90',
  ghost: 'bg-transparent text-fg hover:bg-surface-2',
}

/**
 * The only accented, actionable surface in the system. The focus ring uses
 * the accent colour against a ground-coloured offset, so it reads clearly
 * whether the button sits on the ground or on a raised surface.
 */
export function Button({ variant = 'primary', disabled = false, className = '', type = 'button', ...rest }: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled}
      aria-disabled={disabled}
      className={`rounded-sm px-3 py-1.5 font-mono text-sm font-medium transition-colors motion-reduce:transition-none disabled:cursor-not-allowed disabled:opacity-40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-ground ${VARIANT_CLASS[variant]} ${className}`.trim()}
      {...rest}
    />
  )
}
