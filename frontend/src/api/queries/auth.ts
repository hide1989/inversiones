import { useMutation } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { LoginResponseSchema, type LoginRequest } from '../schemas/auth'

/**
 * Identifies the login mutation to the rest of the app. AuthProvider's
 * global 401 handler reads this key to exclude the login mutation itself
 * from triggering a "session expired" logout — a wrong password is a 401
 * from a request that was never authenticated in the first place, not a
 * session that expired mid-use.
 */
export const loginMutationKey = ['auth', 'login'] as const

export function useLogin() {
  return useMutation({
    mutationKey: loginMutationKey,
    mutationFn: (body: LoginRequest) => apiFetch('/auth/login', LoginResponseSchema, { method: 'POST', body }),
  })
}
