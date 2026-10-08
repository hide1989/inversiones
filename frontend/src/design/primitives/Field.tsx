import { useId } from 'react'
import type { InputHTMLAttributes, Ref } from 'react'

export interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: string
  /**
   * A validation error for this field. Rendered in the app's established
   * error vocabulary (`text-negative`, `role="alert"`) — the same one
   * `ErrorState` and `MutationProblem` already use — rather than the
   * neutral `hint` styling, so a screen reader announces it immediately
   * and a sighted user cannot mistake it for help text. Also sets
   * `aria-invalid` on the input itself.
   */
  invalid?: string
  /** Forwarded to the underlying `<input>`, so a form can move focus to the first invalid field on submit. */
  inputRef?: Ref<HTMLInputElement>
}

/** A labelled input. The label is a real `<label for>`, never a placeholder standing in for one. */
export function Field({ label, hint, invalid, inputRef, id, disabled = false, className = '', ...rest }: FieldProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const hintId = hint !== undefined ? `${inputId}-hint` : undefined
  const invalidId = invalid !== undefined ? `${inputId}-invalid` : undefined
  const describedBy = [hintId, invalidId].filter((value) => value !== undefined).join(' ') || undefined

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={inputId} className="font-mono text-xs font-medium uppercase tracking-wide text-fg-muted">
        {label}
      </label>
      <input
        id={inputId}
        ref={inputRef}
        disabled={disabled}
        aria-disabled={disabled}
        aria-invalid={invalid !== undefined}
        aria-describedby={describedBy}
        className={`rounded-sm border border-border bg-surface-2 px-2 py-1.5 font-mono text-sm text-fg placeholder:text-fg-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:cursor-not-allowed disabled:opacity-40 ${className}`.trim()}
        {...rest}
      />
      {hint !== undefined && (
        <span id={hintId} className="font-sans text-xs text-fg-subtle">
          {hint}
        </span>
      )}
      {invalid !== undefined && (
        <span id={invalidId} role="alert" className="font-sans text-xs text-negative">
          {invalid}
        </span>
      )}
    </div>
  )
}
