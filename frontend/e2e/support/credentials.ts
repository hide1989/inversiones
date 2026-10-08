// The seeded credential for the composed dev backend. It is deliberately
// public in this repository (see spec.md / task-12-brief.md) — never
// treated as a secret, and never logged.
export const SEEDED_USERNAME = 'david'
export const SEEDED_PASSWORD = 'Inversiones2026!'

/** Mirrors AuthProvider.AUTH_STORAGE_KEY — kept here rather than imported, so the e2e suite does not depend on app internals beyond this one string. */
export const AUTH_STORAGE_KEY = 'inversiones.auth'
