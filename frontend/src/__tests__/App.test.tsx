import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import App from '../App'
import { AUTH_STORAGE_KEY } from '../auth/AuthProvider'

function authenticate() {
  sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ token: 'valid-token', username: 'david' }))
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

const ROUTES: ReadonlyArray<{ path: string; heading: RegExp }> = [
  { path: '/posiciones', heading: /posiciones/i },
  { path: '/senales', heading: /señales/i },
  { path: '/transacciones', heading: /transacciones/i },
  { path: '/diversificacion', heading: /diversificación/i },
  { path: '/sintesis-ia', heading: /síntesis ia/i },
  { path: '/earnings', heading: /earnings/i },
  { path: '/watchlist', heading: /watchlist/i },
]

describe('the authenticated route tree', () => {
  beforeEach(() => {
    authenticate()
  })

  it.each(ROUTES)('renders the $path view', async ({ path, heading }) => {
    renderAt(path)
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })

  it('redirects / to /posiciones', async () => {
    renderAt('/')
    expect(await screen.findByRole('heading', { name: /posiciones/i })).toBeInTheDocument()
  })

  it('renders a not-found page for an unknown route rather than a blank screen or a silent redirect', async () => {
    renderAt('/this-route-does-not-exist')
    expect(await screen.findByText(/no encontrada/i)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /posiciones/i })).not.toBeInTheDocument()
  })

  it('has semantic landmarks: header, nav and main', async () => {
    renderAt('/posiciones')
    await screen.findByRole('heading', { name: /posiciones/i })
    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getByRole('navigation')).toBeInTheDocument()
    expect(screen.getByRole('main')).toBeInTheDocument()
  })

  it('marks exactly one nav item as the active page, tracking the current route', async () => {
    renderAt('/senales')
    await screen.findByRole('heading', { name: /señales/i })
    const nav = screen.getByRole('navigation')
    const current = within(nav).getAllByRole('link', { current: 'page' })
    expect(current).toHaveLength(1)
    expect(current[0]).toHaveTextContent(/señales/i)
  })

  it('reaches every nav item and the logout control in keyboard tab order', async () => {
    const user = userEvent.setup()
    renderAt('/posiciones')
    await screen.findByRole('heading', { name: /posiciones/i })

    const nav = screen.getByRole('navigation')
    const navLinks = within(nav).getAllByRole('link')
    const logoutButton = screen.getByRole('button', { name: /cerrar sesión/i })

    const reached: Element[] = []
    document.body.focus()
    for (let i = 0; i < navLinks.length + 1; i++) {
      await user.tab()
      if (document.activeElement) {
        reached.push(document.activeElement)
      }
    }

    for (const link of navLinks) {
      expect(reached).toContain(link)
    }
    expect(reached).toContain(logoutButton)
  })

  it('logs out, clearing the session and landing on /login', async () => {
    const user = userEvent.setup()
    renderAt('/posiciones')
    await screen.findByRole('heading', { name: /posiciones/i })

    await user.click(screen.getByRole('button', { name: /cerrar sesión/i }))

    await waitFor(() => expect(screen.getByRole('heading', { name: /iniciar sesión/i })).toBeInTheDocument())
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })
})

describe('an unauthenticated visit', () => {
  afterEach(() => {
    sessionStorage.clear()
  })

  it('redirects to /login instead of rendering the shell', () => {
    renderAt('/posiciones')
    expect(screen.getByRole('heading', { name: /iniciar sesión/i })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })
})
