import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useLogin } from '../api/queries/auth'
import { ApiError } from '../api/problem'
import { useAuth } from '../auth/useAuth'
import { Button, Field, Surface } from '../design/primitives'

// Mirrors ErrorState's own GENERIC_MESSAGE, which every authenticated
// view already falls back to on a non-ApiError failure. /login renders
// standalone with no shared ErrorState, so without this fallback a
// fetch rejection (backend down, CORS, DNS — a TypeError, not an
// ApiError) showed no message at all: the button simply returned to
// "Iniciar sesión" with nothing on screen to explain why.
const GENERIC_LOGIN_ERROR = 'No se pudo iniciar sesión. Verifica tu conexión e inténtalo de nuevo.'

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

  // The anti-enumeration guarantee lives entirely in the backend's own
  // byte-identical ApiError bodies for a wrong password vs an unknown
  // username — this view never adds its own distinction on top of
  // that. A non-ApiError failure (a TypeError from a rejected fetch) is
  // a different case altogether — not a credentials problem, so the
  // generic fallback here cannot leak anything about enumeration.
  const errorMessage =
    mutation.error instanceof ApiError ? mutation.error.problem.detail : mutation.isError ? GENERIC_LOGIN_ERROR : null

  return (
    // A real `<main>` landmark: unlike every authenticated route (which
    // gets one from AppShell), /login renders standalone with no shell
    // around it, so without this the whole page has zero landmarks —
    // axe's landmark-one-main and region rules both catch that.
    <main className="flex min-h-screen items-center justify-center bg-ground px-4">
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

          {errorMessage !== null && (
            <p role="alert" className="font-sans text-sm text-negative">
              {errorMessage}
            </p>
          )}

          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Iniciando sesión…' : 'Iniciar sesión'}
          </Button>
        </form>
      </Surface>
    </main>
  )
}
