import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/** Where `auth.setup.ts` persists the once-per-run login session, and where `fixtures/authenticated.ts` reads it back from. Kept in its own non-test module: Playwright forbids a test file importing another test file. */
export const AUTH_FILE = path.join(__dirname, '../../playwright/.auth/session.json')
