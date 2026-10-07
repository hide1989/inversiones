import { z } from 'zod'

// Mirrors POST /auth/login's response.
export const LoginResponseSchema = z.object({
  token: z.string(),
  expiresAt: z.string(),
  username: z.string(),
})

export type LoginResponse = z.infer<typeof LoginResponseSchema>

export const LoginRequestSchema = z.object({
  username: z.string(),
  password: z.string(),
})

export type LoginRequest = z.infer<typeof LoginRequestSchema>
