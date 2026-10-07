import { useContext } from 'react'
import { AuthContext, type AuthContextValue } from './AuthProvider'

/** Throws rather than returning undefined, so a missing provider fails loudly at the call site. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (context === null) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
