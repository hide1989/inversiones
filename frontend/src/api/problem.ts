import { z } from 'zod'

// RFC 7807 problem detail, as returned by every error response with
// `application/problem+json`. Validated with Zod like every other
// response body, so a malformed problem response fails the same way a
// malformed success response would — not with an unchecked cast.
//
// `type` is genuinely optional per RFC 7807 §3.1 ("its value is
// 'about:blank'" when absent) and the live backend never sends it —
// confirmed against every error response this app's own e2e suite hits
// (login, transactions, signal refresh). Requiring it here would fail
// ProblemDetailSchema for every single error response the real backend
// produces, silently falling back to rendering the raw JSON body as the
// user-facing message instead of the backend's own `detail` — exactly
// the class of schema-vs-live-response drift this project has hit
// before with other endpoints' fixtures. `.default()` supplies the
// RFC's own default so a present `type` is still validated and an
// absent one does not fail the whole problem.
export const ProblemDetailSchema = z.object({
  type: z.string().default('about:blank'),
  title: z.string(),
  status: z.number(),
  detail: z.string(),
  instance: z.string(),
})

export type ProblemDetail = z.infer<typeof ProblemDetailSchema>

/**
 * Typed error carrying the backend's RFC 7807 problem. Thrown by the API
 * client for any non-2xx response. Carries `status` so callers can branch
 * on it, but a 401 should be distinguished by the `UnauthorizedError`
 * subtype below, not by re-checking `status` — that is what Task 4 routes on.
 */
export class ApiError extends Error {
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail || problem.title || `API error ${problem.status}`)
    this.name = 'ApiError'
    this.problem = problem
  }
}

/** Thrown specifically for a 401, so callers can distinguish it by `instanceof` rather than by status code. */
export class UnauthorizedError extends ApiError {
  constructor(problem: ProblemDetail) {
    super(problem)
    this.name = 'UnauthorizedError'
  }
}

/** A fallback ProblemDetail for an error response that was not `application/problem+json`. */
export function fallbackProblem(status: number, statusText: string, detail: string, instance: string): ProblemDetail {
  return {
    type: 'about:blank',
    title: statusText,
    status,
    detail,
    instance,
  }
}
