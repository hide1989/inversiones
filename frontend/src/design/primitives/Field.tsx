import { useId } from 'react'
import type { InputHTMLAttributes } from 'react'

export interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: string
}

/** A labelled input. The label is a real `<label for>`, never a placeholder standing in for one. */
export function Field({ label, hint, id, disabled = false, className = '', ...rest }: FieldProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const hintId = hint !== undefined ? `${inputId}-hint` : undefined

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="font-mono text-xs font-medium uppercase tracking-wide text-fg-muted">
        {label}
      </label>
      <input
        id={inputId}
        disabled={disabled}
        aria-disabled={disabled}
        aria-describedby={hintId}
        className={`rounded-sm border border-border bg-surface-2 px-2 py-1.5 font-mono text-sm text-fg placeholder:text-fg-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:cursor-not-allowed disabled:opacity-40 ${className}`.trim()}
        {...rest}
      />
      {hint !== undefined && (
        <span id={hintId} className="font-sans text-xs text-fg-subtle">
          {hint}
        </span>
      )}
    </div>
  )
}
