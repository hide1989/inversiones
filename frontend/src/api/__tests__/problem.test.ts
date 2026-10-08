import { describe, expect, it } from 'vitest'
import { ProblemDetailSchema } from '../problem'

// Regression guard for the live-backend schema drift Task 12 found: every
// real error response this app's own e2e suite hits omits `type` entirely
// (confirmed against login, transaction and signal-refresh 4xx/5xx bodies),
// which is valid per RFC 7807 §3.1 ("its value is 'about:blank'" when
// absent). Without this test, reintroducing `type: z.string()` as required
// would only be caught by a live-backend e2e run, not by `npm test`.
describe('ProblemDetailSchema', () => {
  it('accepts a response missing `type`, defaulting it to "about:blank"', () => {
    const result = ProblemDetailSchema.parse({
      title: 'Unauthorized',
      status: 401,
      detail: 'Invalid username or password',
      instance: '/api/v1/auth/login',
    })

    expect(result.type).toBe('about:blank')
  })

  it('still validates a present `type` rather than ignoring it', () => {
    const result = ProblemDetailSchema.parse({
      type: 'https://example.com/probs/out-of-credit',
      title: 'Unauthorized',
      status: 401,
      detail: 'Invalid username or password',
      instance: '/api/v1/auth/login',
    })

    expect(result.type).toBe('https://example.com/probs/out-of-credit')
  })
})
