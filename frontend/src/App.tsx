import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAuth } from './auth/RequireAuth'
import { AppShell } from './components/shell/AppShell'
import { Login } from './views/Login'

// Task 5's route tree: a public /login plus one RequireAuth-guarded /*
// route whose children are the authenticated application. AppShell owns
// the real routing beneath that point — the eight views (seven nav routes
// plus the Specimen route, kept but out of navigation), the not-found
// catch-all, the header/nav/main landmarks and the logout control.
// AuthProvider, RequireAuth and Login are untouched from Task 4.
function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/*"
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        />
      </Routes>
    </AuthProvider>
  )
}

export default App
