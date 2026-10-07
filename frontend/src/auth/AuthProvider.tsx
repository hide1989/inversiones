import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { setAuthToken } from '../api/client'
import { UnauthorizedError } from '../api/problem'

/** sessionStorage, never localStorage: the token must not outlive the tab. */
export const AUTH_STORAGE_KEY = 'inversiones.auth'

const SESSION_EXPIRED_MESSAGE = 'Tu sesión expiró. Inicia sesión de nuevo.'

interface StoredSession {
  token: string
  username: string
}

function readStoredSession(): StoredSession | null {
  const raw = sessionStorage.getItem(AUTH_STORAGE_KEY)
  if (raw === null) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(raw)
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'token' in parsed &&
      'username' in parsed &&
      typeof parsed.token === 'string' &&
      typeof parsed.username === 'string'
    ) {
      return { token: parsed.token, username: parsed.username }
    }
  } catch {
    // Corrupt sessionStorage content: treat as logged out.
  }
  return null
}

export interface AuthContextValue {
  username: string | null
  isAuthenticated: boolean
  /** Set by logout() when it was triggered by a mid-session 401, so Login can show it without relying on router navigation state. */
  sessionMessage: string | null
  login: (token: string, username: string) => void
  logout: (message?: string) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * Owns the auth token as React state, mirrored to sessionStorage for reload
 * survival. Wires the token into the API client's module-level setter, and
 * centralises the one place a 401 from any query is handled: a QueryCache
 * subscription (set up in an effect, not a constructor callback, to avoid
 * closing over stale state without a ref) notices any query settling into
 * an UnauthorizedError and clears the session and routes to /login with a
 * session-expired message. No individual view has to notice a 401 itself.
 *
 * The session-expired message is carried as provider state rather than
 * router navigation state: RequireAuth also redirects to /login on its own
 * (for the "unauthenticated visit" case), and that redirect and this one
 * can both fire for the same transition. Keeping the message in context
 * means Login always finds it regardless of which redirect's navigation
 * state ends up winning.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()

  const [session, setSession] = useState<StoredSession | null>(() => readStoredSession())
  const [sessionMessage, setSessionMessage] = useState<string | null>(null)

  useEffect(() => {
    setAuthToken(session?.token ?? null)
  }, [session])

  const logout = useCallback(
    (message?: string) => {
      sessionStorage.removeItem(AUTH_STORAGE_KEY)
      setSession(null)
      setSessionMessage(message ?? null)
      const from = `${location.pathname}${location.search}`
      navigate('/login', { replace: true, state: { from } })
    },
    [navigate, location],
  )

  const [queryClient] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false } } }))

  useEffect(() => {
    const unsubscribe = queryClient.getQueryCache().subscribe((event) => {
      if (event.type === 'updated' && event.action.type === 'error' && event.action.error instanceof UnauthorizedError) {
        logout(SESSION_EXPIRED_MESSAGE)
      }
    })
    return unsubscribe
  }, [queryClient, logout])

  const login = useCallback((token: string, username: string) => {
    sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ token, username }))
    setSession({ token, username })
    setSessionMessage(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      username: session?.username ?? null,
      isAuthenticated: session !== null,
      sessionMessage,
      login,
      logout,
    }),
    [session, sessionMessage, login, logout],
  )

  return (
    <AuthContext.Provider value={value}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </AuthContext.Provider>
  )
}
