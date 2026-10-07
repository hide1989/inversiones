import type { z } from 'zod'
import { ApiError, fallbackProblem, ProblemDetailSchema, UnauthorizedError, type ProblemDetail } from './problem'

const API_PREFIX = '/api/v1'
const DEFAULT_BASE_URL = 'http://localhost:8080'

function resolveBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL
  return configured && configured.length > 0 ? configured : DEFAULT_BASE_URL
}

let authToken: string | null = null

/** Sets the bearer token the client injects into every request. Task 4 owns where this value comes from. */
export function setAuthToken(token: string | null): void {
  authToken = token
}

/** Thrown when a response fails Zod validation. Names the endpoint and the Zod issue paths, so a contract drift is diagnosable from the message alone. */
export class SchemaValidationError extends Error {
  readonly endpoint: string
  readonly issues: ReadonlyArray<{ path: string; message: string }>

  constructor(endpoint: string, zodError: z.ZodError) {
    const issues = zodError.issues.map((issue) => ({
      path: issue.path.join('.') || '(root)',
      message: issue.message,
    }))
    const detail = issues.map((issue) => `${issue.path}: ${issue.message}`).join('; ')
    super(`${endpoint}: response failed schema validation - ${detail}`)
    this.name = 'SchemaValidationError'
    this.endpoint = endpoint
    this.issues = issues
  }
}

async function readProblem(response: Response, instance: string): Promise<ProblemDetail> {
  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('application/problem+json')) {
    const body: unknown = await response.json()
    const parsed = ProblemDetailSchema.safeParse(body)
    if (parsed.success) {
      return parsed.data
    }
  }
  const detail = await response.text().catch(() => '')
  return fallbackProblem(response.status, response.statusText, detail, instance)
}

export interface ApiFetchOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
}

/**
 * Fetches `path` against the configured API base, injects the bearer
 * token, and validates the response body against `schema` before
 * returning it. This is the only place `fetch` is called in the
 * application — no view touches it directly, and no response reaches a
 * component unvalidated.
 */
export async function apiFetch<Schema extends z.ZodType>(
  path: string,
  schema: Schema,
  options: ApiFetchOptions = {},
): Promise<z.infer<Schema>> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (authToken) {
    headers.set('Authorization', `Bearer ${authToken}`)
  }

  const hasBody = options.body !== undefined
  if (hasBody && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const url = `${resolveBaseUrl()}${API_PREFIX}${path}`
  const response = await fetch(url, {
    ...options,
    headers,
    body: hasBody ? JSON.stringify(options.body) : undefined,
  })

  if (!response.ok) {
    const problem = await readProblem(response, path)
    if (response.status === 401) {
      throw new UnauthorizedError(problem)
    }
    throw new ApiError(problem)
  }

  if (response.status === 204) {
    return schema.parse(undefined)
  }

  const json: unknown = await response.json()
  const result = schema.safeParse(json)
  if (!result.success) {
    throw new SchemaValidationError(path, result.error)
  }
  return result.data
}
