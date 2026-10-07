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

async function submitLoginForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Usuario'), 'david')
  await user.type(screen.getByLabelText('Contraseña'), 'Inversiones2026!')
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
})
