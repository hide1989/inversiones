import { useMutation } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { LoginResponseSchema, type LoginRequest } from '../schemas/auth'

export function useLogin() {
  return useMutation({
    mutationFn: (body: LoginRequest) => apiFetch('/auth/login', LoginResponseSchema, { method: 'POST', body }),
  })
}
