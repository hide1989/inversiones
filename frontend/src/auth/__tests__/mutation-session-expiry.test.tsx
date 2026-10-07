import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider, AUTH_STORAGE_KEY } from '../AuthProvider'
import { RequireAuth } from '../RequireAuth'
import { useAuth } from '../useAuth'
import { useCreateTransaction } from '../../api/queries/transactions'
import { server } from '../../test/msw/server'

const API_BASE = 'http://localhost:8080/api/v1'

function CreateTransactionProbe() {
  const mutation = useCreateTransaction()
  return (
    <div>
      <div>Protected content</div>
      <button
        onClick={() =>
          mutation.mutate({
            ticker: 'QQQ',
            brokerName: 'Hapi',
            type: 'BUY',
            quantity: '1.0000000000',
            unitPrice: '100.0000000000',
            currency: 'USD',
            tradedAt: '2026-01-01',
          })
        }
      >
        Create transaction
      </button>
    </div>
  )
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
                <CreateTransactionProbe />
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

describe('a 401 from a mutation mid-session', () => {
  it('clears the token and redirects to /login with a session-expired message', async () => {
    server.use(
      http.post(`${API_BASE}/transactions`, () =>
        HttpResponse.json(
          {
            type: 'https://api.inversiones.local/errors/unauthorized',
            title: 'Unauthorized',
            status: 401,
            detail: 'missing or invalid token',
            instance: '/api/v1/transactions',
          },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    const user = userEvent.setup()
    renderApp()

    await user.click(screen.getByRole('button', { name: 'Create transaction' }))

    await waitFor(() => expect(screen.getByText('Login screen')).toBeInTheDocument())
    expect(screen.queryByText('Protected content')).not.toBeInTheDocument()
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
    expect(screen.getByTestId('session-message')).toHaveTextContent(/sesión|session/i)
  })
})
