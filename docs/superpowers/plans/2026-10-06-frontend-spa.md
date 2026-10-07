# Frontend SPA Implementation Plan (Plan 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A React single-page application that replaces the 1,546-line static dashboard, consuming the Plan 2 API, with unit and acceptance tests and a design that reads as a financial terminal rather than a generic admin panel.

**Architecture:** Vite builds a static bundle — no Node runtime in production, which is what makes Vercel viable in Plan 4. TanStack Query owns server state; there is no client-side store duplicating it. Zod validates every response at the boundary, so a backend contract change fails loudly in one place instead of producing `undefined` three components deep. No business arithmetic happens in the browser: the backend computes every total, and the frontend formats.

**Tech Stack:** Vite 6, React 19, TypeScript strict, Tailwind CSS 4, TanStack Query 5, React Router 7, Zod 3, Recharts 2, Vitest + React Testing Library + MSW, Playwright + axe.

**Spec:** `spec.md` sections 5 and 6.3.
**Predecessors:** Plan 1 (domain core, 12 tasks) and Plan 2 (infrastructure and API, 12 tasks) are complete: 283 unit tests, 11 acceptance scenarios, merged coverage 96.5%.

---

## The API contract, verbatim

Captured from the running backend's DTOs, not from documentation. This is the authority; if an implementation disagrees with this table, the implementation is wrong.

**Base:** `/api/v1`. Every endpoint except `POST /auth/login` requires `Authorization: Bearer <token>`.

| Method | Path | Returns |
|---|---|---|
| POST | `/auth/login` | `{token, expiresAt, username}` |
| GET | `/portfolio` | `PortfolioResponse` |
| GET | `/portfolio/returns` | `TotalReturnResponse` |
| GET | `/transactions` | `TransactionResponse[]` |
| POST | `/transactions` | `TransactionResponse` |
| PUT | `/transactions/{id}` | `TransactionResponse` |
| DELETE | `/transactions/{id}` | 204 |
| GET | `/signals` | `SignalsDashboardResponse` |
| GET | `/signals/history?ticker=&from=&to=` | `SignalSnapshotDto[]` |
| GET | `/ai-synthesis` | `AiSynthesisResponse` |
| GET | `/earnings` | `EarningsEventDto[]` |
| GET | `/watchlist` | `WatchlistItemDto[]` |
| GET | `/fundamentals/{ticker}` | `FundamentalsDto` |
| GET | `/diversification` | `DiversificationReportDto` |

```ts
Money              = { amount: string; currency: "USD" | "COP" }

PortfolioResponse  = { positions: PositionDto[]; totalInvested: Money;
                       totalMarketValue: Money; totalUnrealizedPnl: Money;
                       unpricedTickers: string[]; baseCurrency: string }

PositionDto        = { ticker: string; brokerName: string; quantity: string;
                       totalCost: Money; averageCost: Money; realizedPnl: Money;
                       marketValue: Money; unrealizedPnl: Money;
                       unrealizedPnlPercent: string; dayChangePercent: string;
                       priced: boolean;
                       costInBaseCurrency: Money; marketValueInBaseCurrency: Money }

TotalReturnResponse = { segments: ReturnSegmentDto[]; baseRate: ExchangeRateDto;
                        currentRate: ExchangeRateDto | null;
                        currencyEffectAvailable: boolean;
                        exchangeEffect: Money | null;
                        assetGainInCop: Money | null;
                        totalReturnInCop: Money | null }

ReturnSegmentDto   = { segment: "USD_ASSETS" | "TRII_ADR" | "BVC_COLOMBIA";
                       invested: Money; marketValue: Money; assetGain: Money }
ExchangeRateDto    = { base: string; quote: string; rate: string; rateDate: string }

SignalsDashboardResponse = { signals: TickerSignalDto[]; averageScore: string;
                             levelCounts: Record<string, number>;
                             tickersWithoutData: string[] }
TickerSignalDto    = { ticker: string; snapshot: SignalSnapshotDto; score: CombinedScoreDto }
SignalSnapshotDto  = { ticker: string; snapshotDate: string; scores: Record<string, string> }
CombinedScoreDto   = { value: string; level: string; usedSources: string[] }

DiversificationReportDto = { violations: DiversificationViolationDto[];
                             totalMarketValue: Money;
                             weightByTicker: Record<string, string>;
                             weightBySector: Record<string, string>;
                             compliant: boolean }
DiversificationViolationDto = { limit: string; subject: string;
                                actualWeight: string; allowedWeight: string }

TransactionRequest  = { ticker: string; brokerName: string; type: "BUY" | "SELL";
                        quantity: string; unitPrice: string;
                        currency: "USD" | "COP"; fees?: string; tradedAt: string }
TransactionResponse = { id: number; ticker: string; brokerName: string; type: string;
                        quantity: string; unitPrice: Money; fees: Money;
                        tradedAt: string; grossAmount: Money; netAmount: Money }

AiSynthesisResponse = { withData: AiSynthesisDto[]; withoutData: string[]; averageScore: string }
AiSynthesisDto      = { ticker: string; snapshotDate: string; iaScore: string; signal: string;
                        conviction: string; horizon: string; summary: string;
                        catalysts: string[]; risks: string[] }
EarningsEventDto    = { ticker: string; reportDate: string; daysRemaining: number;
                        estimatedEps: string; historicalSurprise: string }
WatchlistItemDto    = { ticker: string; price: string; technicalScore: string;
                        wsbScore: string; combinedScore: string; signal: string }
FundamentalsDto     = { ticker: string; isEtf: boolean; hasData: boolean;
                        per: string; peg: string; priceToBook: string; priceToSales: string;
                        evToEbitda: string; roe: string; netMargin: string;
                        revenueCagr5y: string; epsCagr5y: string; debtToEquity: string;
                        beta: string; dividendYield: string; currentRatio: string }
```

Errors are RFC 7807 `ProblemDetail` with `application/problem+json`: `{type, title, status, detail, instance}`. `401` on a missing or invalid token, `404` for an unknown transaction id or unresolved ticker, `400` for a business-rule rejection or a validation failure.

---

## Global Constraints

### Four non-negotiables, each with the failure it prevents

**1. Never parse a monetary `amount` into a JavaScript `number`.**
Every amount arrives as a string carrying scale 10 precisely because `Number("300.0000000000")` silently loses it. A `Money` type keeps the string, and display formatting works from the string. **The frontend performs no monetary arithmetic at all** — the backend already computes every total, subtotal and percentage. If a view seems to need a sum the API does not provide, that is an API gap to report, not a reduce to write.

**2. `currencyEffectAvailable: false` must never render as a zero.**
This is the shape of a Critical defect the backend already paid for: when the TRM source is unreachable the API returns `null` for the effect fields and `false` for the flag, saying *the rate is unknown*. Rendering `0`, `—` with no explanation, or an empty cell would restate the exact lie the backend was fixed to stop telling. The UI must say the currency effect is unavailable and why.

**3. `priced: false` means "not valued", and the reason is ambiguous.**
The backend sets it for a position with no quote **and** for a peso position with no exchange rate to convert. One boolean, two causes — a known limitation recorded at the end of Plan 2. The UI must not invent a reason it cannot know. Say the position is not valued, and that it is either missing a price or missing a rate.

**4. Zod validates every response at the boundary.**
A backend change must fail at the fetch, with the path that disagreed, not three components later as `undefined.map`. No `as` casts on API data, no `any`.

### Everything else

- TypeScript **strict**, including `noUncheckedIndexedAccess`. No `any`, no non-null `!` on API data.
- **No client-side duplication of server state.** TanStack Query is the cache; no Redux, no Zustand mirroring a response.
- **Spanish UI copy** (the user's interface language), **English code, comments, identifiers, commits and test names**.
- Accessibility is a requirement, not a pass: semantic landmarks, keyboard-reachable interactives, visible focus, labelled form controls, and colour never the sole carrier of meaning — every gain or loss also carries a sign or an arrow. `@axe-core/playwright` must report zero violations on every view.
- No secret in the bundle. `VITE_API_BASE_URL` is the only environment input; the token lives in memory with `sessionStorage` rehydration, never `localStorage`.
- Conventional Commits in English with the `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>` trailer.
- Everything runs locally: `npm run dev` against the composed backend. Vercel is Plan 4 and is not configured here.

---

## Design direction

The user chose a **dark financial terminal** from three options, selecting this preview:

```
┌─ PORTAFOLIO ─────────────────────────────────┐
│ INVERTIDO      VALOR HOY      P&L       TRM  │
│ $2,463.00      $2,791.42   +328.42   3,912.4 │
└──────────────────────────────────────────────┘
TICKER  BROKER   PRECIO   DÍA%      P&L    SCORE
QQQ     Hapi     612.40  +0.82   +97.80   ▮8.2
```

That is the brief: **data is the interface, chrome disappears.** The aesthetic is a trading terminal — dense, monospaced, legible at a glance, with the restraint of an instrument rather than the decoration of a dashboard template.

**Type.** `IBM Plex Mono` is the dominant face, not an accent: every figure, ticker, and label. It is a real typeface with character — slab-ish terminals, a distinctive `1` and `0` — and it was designed with `IBM Plex Sans`, used only for prose longer than a label (the AI synthesis summaries, empty-state explanations). Both from Google Fonts. **`font-variant-numeric: tabular-nums` on every numeric cell** so columns align and a changing digit does not reflow the row. Never Inter, never a system stack.

**Colour.** Near-black ground (`#0A0B0D`), surfaces raised by layered lightness rather than borders. One accent: amber (`#F5A524`) for what is actionable or focused. Green and red are **semantic only**, reserved for the sign of a change, never for decoration, and always paired with `▲`/`▼` and an explicit sign so the meaning survives colour blindness and greyscale printing. Contrast ≥ 4.5:1 for text, verified rather than assumed.

**Density and rhythm.** The signals table carries fifteen columns and must read without horizontal scrolling at 1440px. Row height is tight, padding is minimal, and the eye is guided by alignment rather than rules. Numbers right-align; tickers left-align; nothing centres.

**Score bars, not badges.** A combined score renders as a short inline bar plus the number — comparable down a column at a glance, which a coloured pill is not.

**Restraint in motion.** One orchestrated reveal on first load with staggered rows; nothing animates on every render, nothing bounces. A terminal that moves constantly is a terminal nobody can read. `prefers-reduced-motion` removes the stagger entirely.

**Atmosphere without ornament.** A faint grain overlay and a barely-there scanline texture on the shell give the surface depth; they must stay below the threshold where they compete with digits. No gradient meshes, no glassmorphism, no purple.

---

## File structure

```
frontend/
├── package.json  vite.config.ts  tsconfig.json  tailwind.config.ts
├── playwright.config.ts  vitest.config.ts  .env.example
├── index.html
└── src/
    ├── main.tsx  App.tsx  router.tsx
    ├── design/          tokens.css, fonts.css, primitives/
    ├── api/             client.ts, schemas/, queries/, money.ts, problem.ts
    ├── auth/            AuthProvider.tsx, useAuth.ts, RequireAuth.tsx
    ├── views/           Login, Positions, Signals, Earnings, Watchlist,
    │                    AiSynthesis, Transactions, Diversification
    │                    (Positions hosts the TotalReturn panel — see Task 7)
    ├── components/      shell/, table/, state/ (loading, error, empty)
    └── test/            msw/ (handlers, fixtures), setup.ts
└── e2e/                 specs/, fixtures/, pages/
```

---

## Task list

| # | Task | Deliverable |
|---|---|---|
| 1 | Scaffold and tooling | `npm run dev`, `build`, `test`, `lint`, `typecheck` all work |
| 2 | Design system | Tokens, fonts, primitives, and a rendered specimen page |
| 3 | API client, Zod schemas, Money | Every response validated; no number parsing |
| 4 | Auth and route guard | Login, token in memory, 401 → login |
| 5 | App shell and navigation | Eight routes, keyboard navigable |
| 6 | Positions | The main table plus summary tiles |
| 7 | Total return panel | The currency effect inside Positions, including its unavailable state |
| 8 | Signals | Fifteen-column sortable table, score bars, history chart |
| 9 | Transactions CRUD | The form that replaces editing JavaScript constants |
| 10 | Diversification | Ceilings, weights, violations |
| 11 | AI synthesis, Earnings, Watchlist, Fundamentals | The four read-only views |
| 12 | Playwright and accessibility | Seven flows, zero axe violations |

---

### Task 1: Scaffold and tooling

**Goal.** A project that builds, type-checks, lints and tests, with nothing else in it. Every later task depends on this, so it is deliberately small and verifiable.

**Files:** `frontend/package.json`, `vite.config.ts`, `tsconfig.json`, `tailwind.config.ts`, `vitest.config.ts`, `.env.example`, `index.html`, `src/main.tsx`, `src/App.tsx`, `eslint.config.js`, `.prettierrc`

- [ ] **Step 1: Create the project**

```bash
cd "/Users/David/PASCUAL BRAVO/PRUEBAS DE SOFTWARE/inversiones"
npm create vite@latest frontend -- --template react-ts
cd frontend && npm install
```

- [ ] **Step 2: Add the dependencies**

```bash
npm install @tanstack/react-query react-router-dom zod recharts
npm install -D tailwindcss @tailwindcss/vite postcss autoprefixer
npm install -D vitest @vitest/coverage-v8 jsdom
npm install -D @testing-library/react @testing-library/user-event @testing-library/jest-dom
npm install -D msw
npm install -D @playwright/test @axe-core/playwright
npm install -D eslint @eslint/js typescript-eslint eslint-plugin-react-hooks
npm install -D eslint-plugin-jsx-a11y prettier
npx playwright install chromium webkit
```

Report the resolved major versions. React must be **19**, Vite **6**, Tailwind **4**. Tailwind 4 is configured through the Vite plugin, not a PostCSS plugin — do not follow a Tailwind 3 guide.

- [ ] **Step 3: TypeScript strict**

`tsconfig.json` must set `"strict": true`, `"noUncheckedIndexedAccess": true`, `"noUnusedLocals": true`, `"noUnusedParameters": true`, `"noFallthroughCasesInSwitch": true`, `"verbatimModuleSyntax": true`. `noUncheckedIndexedAccess` matters here specifically: this application indexes into `Record<string, string>` maps from the API (`scores`, `weightByTicker`, `levelCounts`), and without it TypeScript would promise a value where the API may have none.

- [ ] **Step 4: Scripts**

```json
"scripts": {
  "dev": "vite",
  "build": "tsc --noEmit && vite build",
  "preview": "vite preview",
  "typecheck": "tsc --noEmit",
  "lint": "eslint .",
  "test": "vitest run",
  "test:watch": "vitest",
  "test:coverage": "vitest run --coverage",
  "e2e": "playwright test",
  "e2e:ui": "playwright test --ui"
}
```

`build` runs `tsc --noEmit` first on purpose: Vite's build does not type-check, so without it a type error ships.

- [ ] **Step 5: Environment**

`.env.example` with `VITE_API_BASE_URL=http://localhost:8080` and a comment that it is the only input. Confirm the repository `.gitignore` already ignores `frontend/.env` and does not ignore `.env.example` — verify, do not assume. Add `frontend/node_modules/`, `frontend/dist/`, `frontend/coverage/`, `frontend/playwright-report/`, `frontend/test-results/` if they are not already ignored.

- [ ] **Step 6: Verify and commit**

```bash
npm run typecheck && npm run lint && npm run build && npm run test
```
`test` with no tests yet must not fail the command — configure `vitest` with `passWithNoTests`. Report the output of all four.

```bash
git add frontend .gitignore
git commit -m "$(cat <<'MSG'
build(frontend): scaffold the vite react typescript project

Vite builds a static bundle with no Node runtime, which is what keeps a
CDN deployment possible later. TypeScript runs strict with
noUncheckedIndexedAccess, because this application indexes into record
maps the API may have no entry for, and the build type-checks before
bundling since Vite's build does not.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

### Task 2: Design system

**Goal.** Tokens, fonts and primitives, plus a specimen route that renders every one of them so the design can be judged before any view depends on it.

**Files:** `src/design/tokens.css`, `src/design/fonts.css`, `src/design/primitives/` (`Surface`, `Figure`, `Delta`, `ScoreBar`, `Tag`, `Button`, `Field`), `src/views/Specimen.tsx`, `tailwind.config.ts`

**The design direction is settled** — see the Design direction section above. Implement it; do not substitute another aesthetic. The user chose it from three options.

**Contract:**
- `tokens.css` defines every colour, space, radius and type size as a CSS custom property on `:root`. Tailwind reads them through `@theme`. No hex literal appears in a component.
- `fonts.css` loads `IBM Plex Mono` (400, 500, 600) and `IBM Plex Sans` (400, 500) from Google Fonts with `display=swap`, and declares fallback stacks that are mono and sans respectively — never a default that silently becomes Arial.
- `Figure` renders a numeric value with `font-variant-numeric: tabular-nums` and right alignment. **It takes a formatted string, never a number** — the Money type from Task 3 produces that string, and `Figure` must not be able to receive a `number` at all. Enforce it in the prop type.
- `Delta` renders a signed change: colour **plus** an explicit `+`/`−` **plus** `▲`/`▼`. The sign and arrow are not optional and not configurable off; colour alone would fail both colour-blind users and a greyscale print.
- `ScoreBar` renders a 1–10 score as a short bar plus the number, taking the score as a string.
- `Tag` renders a signal level or a broker name with no colour-only meaning.
- Focus is visible on every interactive primitive, with a ring that meets contrast against both the ground and the raised surface.
- `prefers-reduced-motion: reduce` removes transitions and the load stagger.

**Tests** (Vitest + RTL):
1. `Figure` applies `tabular-nums` and renders the string unchanged — no reformatting, no rounding.
2. `Figure`'s prop type rejects a `number`: a `@ts-expect-error` line that fails `typecheck` if the type ever widens.
3. `Delta` renders the sign and the arrow for a positive, a negative and a zero value, and the zero case picks neither arrow.
4. `ScoreBar` renders the numeric score as text, so it is reachable by a screen reader and not conveyed by width alone.
5. A primitive with a disabled state is not focusable and is marked `aria-disabled`.
6. Contrast: assert the computed token values for text-on-ground and text-on-surface meet 4.5:1, computed in the test rather than eyeballed.

- [ ] **Step 1** Write the failing tests. **Step 2** Run them, confirm they fail. **Step 3** Implement tokens, fonts and primitives. **Step 4** Build the specimen route rendering every primitive in every state. **Step 5** Run `npm run test && npm run typecheck && npm run build`. **Step 6** Commit.

---

### Task 3: API client, Zod schemas and Money

**Goal.** One validated boundary. After this task no view touches `fetch`, and no response reaches a component unvalidated.

**Files:** `src/api/client.ts`, `src/api/money.ts`, `src/api/problem.ts`, `src/api/schemas/*.ts`, `src/api/queries/*.ts`

**Contract:**
- `money.ts` exports `Money` (the validated `{amount, currency}`), and `formatMoney(money, options)` returning a **string**, built from the string amount. It must not call `Number()`, `parseFloat` or `+` on the amount. A test asserts that formatting `"300.0000000000"` and `"1234567.8900000000"` keeps every significant digit the display asks for and never shows `1.2345678899999999e6`.
- Percentages and scores arrive as strings too; `formatPercent` and `formatScore` follow the same rule.
- `client.ts` wraps `fetch`: injects the bearer token, sets `Accept`, parses `application/problem+json` into a typed `ProblemDetail`, and throws a typed error carrying the status. A `401` is distinguishable by type, because Task 4 routes on it.
- Every endpoint has a Zod schema mirroring the contract table above. `schema.parse` runs on every response. On failure the thrown error names the endpoint and the Zod path — a contract drift must be diagnosable from the message alone.
- `queries/` exports one TanStack Query hook per endpoint with a stable key, so cache invalidation after a transaction mutation is explicit rather than a blanket refetch.

**Tests** (Vitest + MSW):
1. `formatMoney` preserves precision for a scale-10 amount and for a large amount; no exponent notation.
2. `money.ts` contains no `Number(`, `parseFloat`, or unary `+` on an amount — asserted by reading the module source in the test, so the rule survives a refactor.
3. A valid `PortfolioResponse` parses; the hook returns typed data.
4. A response missing `baseCurrency` throws, and the message names `baseCurrency`.
5. A response where `totalInvested.amount` is a **number** instead of a string throws — this is the regression that would mean the backend started serialising money as JSON numbers.
6. A `problem+json` 400 becomes a typed `ProblemDetail` with `detail` preserved.
7. A `401` throws the distinguishable unauthorized error type.
8. `TotalReturnResponse` with `currencyEffectAvailable: false` and `null` effect fields parses successfully — the schema must accept the unavailable shape rather than rejecting it as malformed.

- [ ] Tests first, confirm failing, implement, verify, commit.

---

### Task 4: Auth and route guard

**Contract:**
- `POST /auth/login` on submit; token held in React state, mirrored to `sessionStorage` for reload survival. **Never `localStorage`** — it persists past the browser session and is readable by any script that gets injected.
- `RequireAuth` redirects to `/login` preserving the attempted path, and returns there after a successful login.
- Any `401` from any query clears the token and routes to `/login` with a message saying the session expired. One place, not per-view handling.
- The login form shows the API's `detail` on failure. The backend returns byte-identical bodies for a wrong password and an unknown username; the UI must not add a distinction (no "user not found" copy), because that would reintroduce username enumeration the backend deliberately prevents.
- The form is labelled, submits on Enter, disables while in flight, and surfaces errors to assistive technology via `role="alert"`.

**Tests** (Vitest + RTL + MSW): successful login stores the token and redirects; a 401 shows the API detail and does not store a token; a protected route unauthenticated redirects to `/login`; a 401 mid-session clears and redirects; the token is absent from `localStorage` after login — asserted explicitly.

- [ ] Tests first, confirm failing, implement, verify, commit.

---

### Task 5: App shell and navigation

**Contract:** the seven routes `spec.md` §5.3 defines — `/posiciones`, `/senales`, `/transacciones`, `/diversificacion`, `/sintesis-ia`, `/earnings`, `/watchlist` — plus `/login`, for eight in total. `/` redirects to `/posiciones`. There is deliberately **no** `/retornos` route: the spec places the total return inside `/posiciones`, and Task 7 builds it as a panel there. Semantic landmarks (`header`, `nav`, `main`). Navigation reachable and operable by keyboard, with the current route marked `aria-current="page"`. A single `ErrorBoundary` and the three shared states (loading skeleton, actionable error with retry, explicit empty) live here so every view uses the same ones. Lazy-load route components so the initial bundle stays small; report the built bundle size.

**Tests:** each route renders its view; an unknown route shows a not-found page; `aria-current` tracks the active route; keyboard tab order reaches every nav item; the error boundary renders a retry affordance rather than a blank screen.

- [ ] Tests first, confirm failing, implement, verify, commit.

---

### Task 6: Positions

The main view, replacing the old dashboard's first tab.

**Contract:**
- Summary tiles: invested, market value, unrealized P&L, and the declared base currency. Every figure from `PortfolioResponse`; **nothing computed in the browser**.
- Two tables, as the old dashboard had: NYSE/NASDAQ holdings and Trii/BVC holdings, grouped by the position's currency.
- Columns: ticker, broker, quantity, average cost, market value, day change, unrealized P&L, unrealized P&L percent.
- **A position with `priced: false` is visually distinct and labelled "sin valorar"**, with an explanation that it is either missing a price or missing an exchange rate — the API cannot say which, and the UI must not guess. `unpricedTickers` drives a summary note so the user knows the totals exclude a market value for them.
- `baseCurrency` is displayed, not assumed. The totals are in it; the per-position native amounts are not.
- Sortable by any numeric column, sort state in the URL so a view can be linked.

**Tests:** renders the seeded portfolio from an MSW fixture captured from the real API; an unpriced position shows the label and the explanation and is excluded from no total the API included it in; the base currency is rendered; sorting by P&L reorders rows and updates the URL; a loading state shows skeletons; a 500 shows the error state with retry; an empty portfolio shows the empty state.

- [ ] Tests first, confirm failing, implement, verify, commit.

---

### Task 7: The total return panel — the currency effect

**Where this lives.** `spec.md` §5.3 places the total return inside `/posiciones`, so this is a panel on that route, not a route of its own. It is a separate *task* because it is a distinct deliverable with its own data source (`GET /portfolio/returns`), its own states and its own tests — the task structure need not mirror the route structure. Give it a full-width panel with its own loading, error and unavailable states rather than a cramped footer strip; the old dashboard buried it at the foot of a table, and burying it is what this plan is trying to stop.

**This panel exists to make one distinction visible**: how much of the result came from assets moving and how much from the peso moving. It is the reason the backend separates them, and the whole point of the migration.

**Contract:**
- The three segments (`USD_ASSETS`, `TRII_ADR`, `BVC_COLOMBIA`) with invested, market value and asset gain each.
- The asset gain and the exchange effect **side by side, never summed into one figure** in the primary display. The total is shown, but subordinate to the two components.
- Both rates displayed with their dates: the base TRM and the current one.
- **When `currencyEffectAvailable` is `false`:** the effect, the asset gain in COP and the total are `null`. The view must state that the exchange rate is unavailable and therefore the currency effect cannot be computed — **not** render `0`, not render `—` without explanation, not hide the section. This is non-negotiable #2; a test asserts the literal absence of a `0` in that region.
- A chart only if it clarifies: a two-bar comparison of asset gain versus currency effect is defensible; a pie chart of three segments is decoration.

**Tests:** the available case renders both components separately and both rates with dates; **the unavailable case renders an explanation and no zero** — assert on the rendered text, not on a prop; the three segments render with their labels in Spanish; a peso-only portfolio and a dollar-only portfolio both render correctly.

- [ ] Tests first, confirm failing, implement, verify, commit.

---

### Task 8: Signals

**Contract:**
- The dense table: ticker, the six per-source scores, combined score with a `ScoreBar`, level, and the count of sources used. Fifteen columns readable at 1440px without horizontal scroll.
- Sorted by combined score descending by default, matching the API's order and the old dashboard's behaviour.
- **A ticker in `tickersWithoutData` is shown but visually separated and labelled "sin datos"**, and must not render a score of `0.00` or a level of `STRONG_SELL` as though they were an assessment. The API returns a zero-source score as `0.00`/`STRONG_SELL` by its threshold table, with `usedSources` empty as the marker — the UI reads `usedSources.length === 0`, not the value.
- **A score built from three sources is a weaker claim than one built from six**, and the UI must show that: render the count of contributing sources beside every score. This is the whole reason `usedSources` is in the contract.
- `averageScore` and `levelCounts` as summary tiles.
- A per-ticker history chart from `/signals/history`, loaded on demand rather than for every ticker on mount.

**Tests:** the table renders in score-descending order; a no-data ticker shows the label and does not display `0.00` as a score or a sell level — asserted on rendered text; the source count renders beside each score; sorting by a per-source column works; the history chart fetches only when a ticker is opened; the summary tiles render from the API values.

- [ ] Tests first, confirm failing, implement, verify, commit.

---

### Task 9: Transactions CRUD

**The view that ends editing JavaScript constants to change the portfolio.**

**Contract:**
- A table of transactions with filters for ticker, broker and date range, driving the API's query parameters rather than filtering client-side.
- A create/edit form: ticker, broker, type, quantity, unit price, currency, fees, trade date. Amounts are **text inputs** producing decimal strings, never `<input type="number">` — a number input coerces through a float and would corrupt scale 10 before the request leaves the browser.
- Client-side validation mirrors the API's shape rules only (non-blank, positive decimal pattern). **Business rules are not duplicated** — a future trade date, a sale exceeding the holding, an unknown ticker and an instrument change on edit are all the backend's to reject, and the UI renders the returned `ProblemDetail`. Duplicating them creates two places to change and two chances to disagree.
- Delete asks for confirmation naming the transaction.
- After any mutation, the affected queries are invalidated explicitly: transactions, portfolio, returns and diversification all change when a trade is recorded.

**Tests:** the list renders and filters hit the API with the right query string; creating a valid transaction posts the exact request shape and invalidates the portfolio query; a server 400 renders the `ProblemDetail` detail; amount fields are `type="text"` and a pasted `0.486372` survives unchanged — asserted on the submitted body; delete confirms before calling the API; editing to a future date shows the server's rejection rather than a client-side block.

- [ ] Tests first, confirm failing, implement, verify, commit.

---

### Task 10: Diversification

**Contract:** the three ceilings (20% single stock, 25% single fund, 30% sector) with each weight against its limit; violations listed with subject, actual and allowed; `compliant` driving a clear overall state. Weights come from `weightByTicker` and `weightBySector` as strings. A bar-per-weight against a ceiling line communicates this better than a pie; the ceiling must be a visible reference, not implied.

**Tests:** a compliant report renders the compliant state and no violations; a report with a ticker violation and a sector violation renders both with their subjects and weights; weights render from the API strings without re-computation; the empty portfolio case renders the empty state.

- [ ] Tests first, confirm failing, implement, verify, commit.

---

### Task 11: AI synthesis, Earnings, Watchlist, Fundamentals

Four read-only views, lower density, more prose.

**Contract:**
- **AI synthesis:** a card per ticker with score, signal, conviction, horizon, summary, catalysts and risks. `withoutData` tickers are listed as explicitly without data, and `averageScore` is stated as computed from the tickers that had data — the backend excludes the others, and the UI must say so rather than implying the average covers everything. The summary is prose: this is where `IBM Plex Sans` earns its place.
- **Earnings:** ordered by proximity, with days remaining, estimated EPS and historical surprise.
- **Watchlist:** ticker, price, technical and WSB scores, combined score, signal.
- **Fundamentals:** fetched per ticker on demand. `hasData: false` renders an explicit no-data state; `isEtf` changes which multiples are meaningful, so do not render an empty P/E grid for a fund.

**Tests:** AI synthesis renders a card and lists the no-data tickers, and states what the average covers; earnings render in proximity order; a fundamentals response with `hasData: false` shows the no-data state and no empty metric grid; an ETF renders the fund-appropriate fields.

- [ ] Tests first, confirm failing, implement, verify, commit.

---

### Task 12: Playwright and accessibility

**Contract:**
- Page objects per view; an auth fixture that logs in once and reuses storage state, so each spec does not re-authenticate.
- Runs against the **composed backend** (`docker compose -f deploy/docker-compose.dev.yml up -d`) and the Vite preview build, not the dev server — the production bundle is what ships.
- Chromium and WebKit. Traces and screenshots on failure only.
- `@axe-core/playwright` on **every** route, login included, asserting zero violations.

**The seven flows** from spec §6.3:
1. Login and logout.
2. The positions dashboard loads and shows the seeded portfolio.
3. Sorting the signals table.
4. Registering a purchase and seeing it reflected in positions.
5. Editing a transaction and seeing the recalculated average cost.
6. Deleting a transaction.
7. Accessibility on every route.

**Plus the one that matters most, and that no unit test can prove:**
8. **With the TRM source unavailable, the returns view states the currency effect is unavailable and renders no zero in its place.** This is the end-to-end proof that the frontend does not undo the backend's Critical fix. Drive it by pointing the backend at an unreachable n8n, or by intercepting the route — say which and why.

**Tests:** all eight green in both browsers.

- [ ] Write the specs, run them, implement any missing `data-testid` hooks, verify, commit.

---

## Definition of Done

- [ ] `npm run typecheck && npm run lint && npm run build && npm run test` green.
- [ ] `npm run e2e` green in Chromium and WebKit, including flow 8.
- [ ] Zero `@axe-core/playwright` violations on all eight routes, login included.
- [ ] No `Number(`, `parseFloat` or unary `+` applied to an API monetary amount anywhere — asserted by a test, not by inspection.
- [ ] `currencyEffectAvailable: false` renders an explanation and no zero, proven by a unit test and an e2e flow.
- [ ] `priced: false` renders "sin valorar" with both possible reasons named.
- [ ] A zero-source signal renders as "sin datos", never as `0.00`/`STRONG_SELL`.
- [ ] No token in `localStorage`; no secret in the bundle.
- [ ] Every API response Zod-validated; no `as` cast and no `any` on API data.
- [ ] UI copy in Spanish; code, commits and test names in English.

## Out of scope

Plan 4 owns deployment: the Vercel project and its environment variables, the VPS compose file behind the existing Traefik, and Jenkins with a GitHub webhook. Nothing here requires a cloud account.

## Deviations from the spec, and why

**None in routing.** An earlier draft of this plan added a ninth route, `/retornos`, for the total
return. `spec.md` §5.3 — which the author approved — places it inside `/posiciones`, mirroring the
layout of the dashboard being replaced. The draft was corrected to follow the spec: no new route, no
new nav item, and the data appears where the author expects it. What the plan keeps from the
reasoning behind the split is the insistence that the panel gets real prominence and its own
unavailable state, because the currency effect being visible is the point of the backend's Critical
fix. If the panel turns out to crowd the page in practice, promoting it to its own route later is a
routing change and nothing more.

**One addition to the acceptance flows.** `spec.md` §6.3 names seven Playwright flows; this plan adds
an eighth — the returns panel with the TRM source unavailable. Plan 2's final review named it the
highest-value missing scenario, and it is the only end-to-end proof that the frontend does not undo
the Critical fix by rendering a zero where the backend says "unknown".

## Known API limitations to work around, not hide

1. `priced: false` conflates "no quote" with "no exchange rate" — recorded at the end of Plan 2. The UI names both possibilities.
2. `/earnings` returns an empty body from the live n8n workflow (verified). The view must render its empty state cleanly, since that is today's actual behaviour.
3. `GET /signals` serves persisted snapshots, so it answers with valid data even when n8n is down. There is no staleness indicator in the contract; the view shows `snapshotDate` so the user can judge the age themselves.
