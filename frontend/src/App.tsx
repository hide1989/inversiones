import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAuth } from './auth/RequireAuth'
import { Login } from './views/Login'
import { Specimen } from './views/Specimen'

// Minimal routing for Task 4's own needs: a public /login and one
// RequireAuth-guarded route standing in for the authenticated app. Task 5
// owns the real route tree, the eight views, the shell and the shared
// loading/error/empty states — it replaces the single protected route
// below with its own <Route> elements (still wrapped in RequireAuth) and
// does not need to touch AuthProvider, RequireAuth or Login.
function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/*"
          element={
            <RequireAuth>
              <div className="app-shell">
                <Specimen />
              </div>
            </RequireAuth>
          }
        />
      </Routes>
    </AuthProvider>
  )
}

export default App
