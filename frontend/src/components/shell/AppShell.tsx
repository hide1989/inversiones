import { lazy, Suspense } from 'react'
import { Navigate, NavLink, Route, Routes } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { Button } from '../../design/primitives'
import { LoadingState } from '../state'
import { ErrorBoundary } from './ErrorBoundary'
import { NotFound } from '../../views/NotFound'

// Each view is its own chunk, fetched only when its route is visited, so the
// initial bundle does not carry seven views' worth of code the first screen
// never needs. The shared LoadingState is the Suspense fallback, so a slow
// chunk fetch reads the same as a slow data fetch rather than a blank pause.
const Posiciones = lazy(() => import('../../views/Posiciones').then((m) => ({ default: m.Posiciones })))
const Senales = lazy(() => import('../../views/Senales').then((m) => ({ default: m.Senales })))
const Transacciones = lazy(() => import('../../views/Transacciones').then((m) => ({ default: m.Transacciones })))
const Diversificacion = lazy(() =>
  import('../../views/Diversificacion').then((m) => ({ default: m.Diversificacion })),
)
const SintesisIA = lazy(() => import('../../views/SintesisIA').then((m) => ({ default: m.SintesisIA })))
const Earnings = lazy(() => import('../../views/Earnings').then((m) => ({ default: m.Earnings })))
const Watchlist = lazy(() => import('../../views/Watchlist').then((m) => ({ default: m.Watchlist })))
const Specimen = lazy(() => import('../../views/Specimen').then((m) => ({ default: m.Specimen })))

interface NavItem {
  path: string
  label: string
}

// Exactly the seven authenticated routes spec.md §5.3 defines. No /retornos:
// the total return lives inside /posiciones as a panel (Task 7).
const NAV_ITEMS: ReadonlyArray<NavItem> = [
  { path: '/posiciones', label: 'Posiciones' },
  { path: '/senales', label: 'Señales' },
  { path: '/transacciones', label: 'Transacciones' },
  { path: '/diversificacion', label: 'Diversificación' },
  { path: '/sintesis-ia', label: 'Síntesis IA' },
  { path: '/earnings', label: 'Earnings' },
  { path: '/watchlist', label: 'Watchlist' },
]

function navLinkClassName({ isActive }: { isActive: boolean }): string {
  const base =
    'block rounded-sm px-3 py-2 font-mono text-sm transition-colors motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2 focus-visible:ring-offset-ground'
  return isActive ? `${base} bg-surface-2 text-accent` : `${base} text-fg-muted hover:bg-surface-2 hover:text-fg`
}

/**
 * The authenticated application shell: semantic landmarks (`header`,
 * `nav`, `main`), the eight-view route tree (plus the design system's own
 * Specimen route, reachable but not part of navigation), and the logout
 * control `AuthProvider.logout` otherwise has no caller for.
 */
export function AppShell() {
  const { logout } = useAuth()

  return (
    <div className="app-shell flex min-h-screen flex-col bg-ground">
      <header className="flex items-center justify-between border-b border-border px-6 py-3">
        <span className="font-mono text-sm font-semibold uppercase tracking-wide text-fg">Inversiones</span>
        <Button variant="ghost" onClick={() => logout()}>
          Cerrar sesión
        </Button>
      </header>
      <div className="flex flex-1">
        <nav aria-label="Principal" className="w-56 border-r border-border p-4">
          <ul className="flex flex-col gap-1">
            {NAV_ITEMS.map((item) => (
              <li key={item.path}>
                <NavLink to={item.path} className={navLinkClassName}>
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <main className="flex-1 p-6">
          <ErrorBoundary>
            <Suspense fallback={<LoadingState />}>
              <Routes>
                <Route index element={<Navigate to="posiciones" replace />} />
                <Route path="posiciones" element={<Posiciones />} />
                <Route path="senales" element={<Senales />} />
                <Route path="transacciones" element={<Transacciones />} />
                <Route path="diversificacion" element={<Diversificacion />} />
                <Route path="sintesis-ia" element={<SintesisIA />} />
                <Route path="earnings" element={<Earnings />} />
                <Route path="watchlist" element={<Watchlist />} />
                <Route path="specimen" element={<Specimen />} />
                <Route path="*" element={<NotFound />} />
              </Routes>
            </Suspense>
          </ErrorBoundary>
        </main>
      </div>
    </div>
  )
}
