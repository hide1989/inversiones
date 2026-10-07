import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider, AUTH_STORAGE_KEY } from '../AuthProvider'
import { RequireAuth } from '../RequireAuth'
import { useAuth } from '../useAuth'
import { usePortfolio } from '../../api/queries/portfolio'
import { server } from '../../test/msw/server'

const API_BASE = 'http://localhost:8080/api/v1'

function PortfolioProbe() {
  usePortfolio()
  return <div>Portfolio content</div>
}

function LoginScreenProbe() {
  const { sessionMessage } = useAuth()
  return (
    <div>
      <div>Login screen</div>
      <div data-testid="session-message">{sessionMessage ?? ''}</div>
    </div>
  )
}

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginScreenProbe />} />
          <Route
            path="/protected"
            element={
              <RequireAuth>
                <PortfolioProbe />
              </RequireAuth>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ token: 'stale-token', username: 'david' }))
})

describe('a 401 from a query mid-session', () => {
  it('clears the token and redirects to /login with a session-expired message', async () => {
    server.use(
      http.get(`${API_BASE}/portfolio`, () =>
        HttpResponse.json(
          {
            type: 'https://api.inversiones.local/errors/unauthorized',
            title: 'Unauthorized',
            status: 401,
            detail: 'missing or invalid token',
            instance: '/api/v1/portfolio',
          },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    renderApp()

    await waitFor(() => expect(screen.getByText('Login screen')).toBeInTheDocument())
    expect(screen.queryByText('Portfolio content')).not.toBeInTheDocument()
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    expect(screen.getByTestId('session-message')).toHaveTextContent(/sesión|session/i)
  })
})
