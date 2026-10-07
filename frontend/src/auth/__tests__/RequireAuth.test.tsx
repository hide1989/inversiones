import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthProvider } from '../AuthProvider'
import { RequireAuth } from '../RequireAuth'

function LocationProbe() {
  const location = useLocation()
  const state = location.state
  const from =
    typeof state === 'object' && state !== null && 'from' in state && typeof state.from === 'string' ? state.from : null
  return <div data-testid="attempted-path">{from ?? ''}</div>
}

describe('an unauthenticated visit to a protected route', () => {
  it('redirects to /login and preserves the attempted path', () => {
    render(
      <MemoryRouter initialEntries={['/protected?tab=summary']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/login"
              element={
                <>
                  <div>Login screen</div>
                  <LocationProbe />
                </>
              }
            />
            <Route
              path="/protected"
              element={
                <RequireAuth>
                  <div>Protected content</div>
                </RequireAuth>
              }
            />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(screen.getByText('Login screen')).toBeInTheDocument()
    expect(screen.queryByText('Protected content')).not.toBeInTheDocument()
    expect(screen.getByTestId('attempted-path')).toHaveTextContent('/protected?tab=summary')
  })
})
