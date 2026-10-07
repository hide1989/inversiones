import { createContext, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { setAuthToken } from '../api/client'
import { UnauthorizedError } from '../api/problem'
import { loginMutationKey } from '../api/queries/auth'

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

/** True when a mutation's key matches the login mutation's own key. */
function isLoginMutation(mutationKey: ReadonlyArray<unknown> | undefined): boolean {
  if (mutationKey === undefined || mutationKey.length !== loginMutationKey.length) {
    return false
  }
  return mutationKey.every((part, index) => part === loginMutationKey[index])
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
 * centralises the one place a 401 is handled, from either a query or a
 * mutation: subscriptions to both the QueryCache and the MutationCache (set
 * up in an effect, not a constructor callback, to avoid closing over stale
 * state without a ref) share one handler that notices a query or mutation
 * settling into an UnauthorizedError and clears the session and routes to
 * /login with a session-expired message. No individual view has to notice
 * a 401 itself.
 *
 * One mutation is deliberately exempt: the login mutation itself. A wrong
 * password is a 401 from a request that was never authenticated — it is
 * not a session expiring mid-use — so it must not clear a (non-existent)
 * session or show the session-expired message next to the login form's
 * own error. It is excluded by `mutationKey`, checked explicitly in the
 * mutation subscription, rather than by inspecting the URL or the error:
 * a key-based exclusion is visible at both ends (see `loginMutationKey`
 * in `api/queries/auth.ts`) to the next person reading either side.
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

  // Read through a ref, updated in an effect (never during render — that
  // trips react-hooks/refs), so `logout`'s identity stays stable across
  // navigations instead of churning the cache subscriptions below on
  // every route change.
  const locationRef = useRef(location)
  useEffect(() => {
    locationRef.current = location
  }, [location])

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
      const from = `${locationRef.current.pathname}${locationRef.current.search}`
      navigate('/login', { replace: true, state: { from } })
    },
    [navigate],
  )

  const [queryClient] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false } } }))

  // Shared by both subscriptions below, so the "is this an UnauthorizedError"
  // check and the resulting action can't drift apart between the two caches.
  const handleUnauthorizedError = useCallback(
    (error: unknown) => {
      if (error instanceof UnauthorizedError) {
        logout(SESSION_EXPIRED_MESSAGE)
      }
    },
    [logout],
  )

  useEffect(() => {
    const unsubscribeQueries = queryClient.getQueryCache().subscribe((event) => {
      if (event.type === 'updated' && event.action.type === 'error') {
        handleUnauthorizedError(event.action.error)
      }
    })
    const unsubscribeMutations = queryClient.getMutationCache().subscribe((event) => {
      if (event.type === 'updated' && event.action.type === 'error' && !isLoginMutation(event.mutation.options.mutationKey)) {
        handleUnauthorizedError(event.action.error)
      }
    })
    return () => {
      unsubscribeQueries()
      unsubscribeMutations()
    }
  }, [queryClient, handleUnauthorizedError])

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
