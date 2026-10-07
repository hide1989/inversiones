import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider } from '../AuthProvider'
import { RequireAuth } from '../RequireAuth'
import { Login } from '../../views/Login'
import { server } from '../../test/msw/server'
import { invalidLoginProblem } from '../../test/msw/fixtures'

const API_BASE = 'http://localhost:8080/api/v1'

function LocationProbe() {
  const location = useLocation()
  const state = location.state
  const from =
    typeof state === 'object' && state !== null && 'from' in state && typeof state.from === 'string' ? state.from : null
  return <div data-testid="attempted-path">{from ?? ''}</div>
}

function renderApp(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<div>Home default</div>} />
          <Route
            path="/login"
            element={
              <>
                <Login />
                <LocationProbe />
              </>
            }
          />
          <Route
            path="/protected"
            element={
              <RequireAuth>
                <div>Portfolio content</div>
              </RequireAuth>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function submitLoginForm(user: ReturnType<typeof userEvent.setup>, password = 'Inversiones2026!') {
  const usernameField = screen.getByLabelText('Usuario')
  const passwordField = screen.getByLabelText('Contraseña')
  await user.clear(usernameField)
  await user.type(usernameField, 'david')
  await user.clear(passwordField)
  await user.type(passwordField, password)
  await user.click(screen.getByRole('button', { name: /iniciar sesión/i }))
}

describe('a successful login', () => {
  it('stores the token and redirects to the attempted path, not the default route', async () => {
    const user = userEvent.setup()
    renderApp('/protected')

    await submitLoginForm(user)

    await waitFor(() => expect(screen.getByText('Portfolio content')).toBeInTheDocument())
    expect(screen.queryByText('Home default')).not.toBeInTheDocument()
  })

  it('does not leak the token into localStorage', async () => {
    const user = userEvent.setup()
    renderApp('/protected')

    await submitLoginForm(user)

    await waitFor(() => expect(screen.getByText('Portfolio content')).toBeInTheDocument())
    expect(localStorage.length).toBe(0)
  })
})

describe('a failed login', () => {
  it('shows the API detail verbatim and stores no token', async () => {
    server.use(
      http.post(`${API_BASE}/auth/login`, () =>
        HttpResponse.json(invalidLoginProblem, {
          status: 401,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    )

    const user = userEvent.setup()
    renderApp('/login')

    await user.type(screen.getByLabelText('Usuario'), 'david')
    await user.type(screen.getByLabelText('Contraseña'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: /iniciar sesión/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(invalidLoginProblem.detail)
    expect(screen.queryByText('Portfolio content')).not.toBeInTheDocument()
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })

  it('does not also show the session-expired message: a wrong password is not a mid-session logout', async () => {
    server.use(
      http.post(`${API_BASE}/auth/login`, () =>
        HttpResponse.json(invalidLoginProblem, {
          status: 401,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    )

    const user = userEvent.setup()
    renderApp('/login')

    await user.type(screen.getByLabelText('Usuario'), 'david')
    await user.type(screen.getByLabelText('Contraseña'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: /iniciar sesión/i }))

    await screen.findByRole('alert')
    // The session-expired banner is rendered with role="status"; a failed
    // login must never trigger it alongside the login form's own error.
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.queryByText(/sesión expiró/i)).not.toBeInTheDocument()
  })
})

describe('a failed login followed by a successful one', () => {
  it('reaches the originally attempted path, not the default route', async () => {
    server.use(
      http.post(
        `${API_BASE}/auth/login`,
        () =>
          HttpResponse.json(invalidLoginProblem, {
            status: 401,
            headers: { 'Content-Type': 'application/problem+json' },
          }),
        { once: true },
      ),
    )

    const user = userEvent.setup()
    renderApp('/protected')

    // First attempt fails (the overridden handler above answers once)...
    await submitLoginForm(user, 'wrong-password')
    await screen.findByRole('alert')

    // ...and the retry, now hitting the default success handler, must
    // still land on /protected — not on "/" — because the failed attempt
    // must not have corrupted the remembered "from" path.
    await submitLoginForm(user)

    await waitFor(() => expect(screen.getByText('Portfolio content')).toBeInTheDocument())
    expect(screen.queryByText('Home default')).not.toBeInTheDocument()
  })
})
