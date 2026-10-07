import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useLogin } from '../api/queries/auth'
import { ApiError } from '../api/problem'
import { useAuth } from '../auth/useAuth'
import { Button, Field, Surface } from '../design/primitives'

function readFromState(state: unknown): string | null {
  if (typeof state !== 'object' || state === null) {
    return null
  }
  return 'from' in state && typeof state.from === 'string' ? state.from : null
}

/**
 * The login form. It never adds its own distinction between a wrong
 * password and an unknown username — both render the API's `detail`
 * unchanged, because the backend already made those two bodies
 * byte-identical to prevent username enumeration, and any UI-side copy
 * path would reintroduce exactly that leak.
 */
export function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const { login, sessionMessage } = useAuth()
  const mutation = useLogin()

  const destination = readFromState(location.state) ?? '/'

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      const result = await mutation.mutateAsync({ username, password })
      login(result.token, result.username)
      navigate(destination, { replace: true })
    } catch {
      // Swallowed: mutation.error below drives the rendered error message.
    }
  }

  const errorDetail = mutation.error instanceof ApiError ? mutation.error.problem.detail : null

  return (
    <div className="flex min-h-screen items-center justify-center bg-ground px-4">
      <Surface level={1} className="w-full max-w-sm p-6">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <h1 className="font-mono text-sm font-medium uppercase tracking-wide text-fg">Iniciar sesión</h1>

          {sessionMessage !== null && (
            <p role="status" className="font-sans text-sm text-fg-muted">
              {sessionMessage}
            </p>
          )}

          <Field
            label="Usuario"
            name="username"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            disabled={mutation.isPending}
            required
          />
          <Field
            label="Contraseña"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={mutation.isPending}
            required
          />

          {errorDetail !== null && (
            <p role="alert" className="font-sans text-sm text-negative">
              {errorDetail}
            </p>
          )}

          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Iniciando sesión…' : 'Iniciar sesión'}
          </Button>
        </form>
      </Surface>
    </div>
  )
}
