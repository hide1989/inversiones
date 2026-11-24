/**
 * The endpoint catalogue, classified by whether a request can reach an external system.
 *
 * ------------------------------------------------------------------------------------------------
 * THE CLASSIFICATION RULE, AND WHY THE FIRST VERSION OF IT WAS WRONG
 *
 * The first version of this file classified `/portfolio` and `/portfolio/returns` as safe to load,
 * reasoning that their n8n calls are cached — quotes for 5 minutes, the TRM until midnight — so
 * repeated requests would hit Caffeine instead of the network. Measurement supported it: three
 * sequential calls dropped to ~12 ms.
 *
 * Running the stress scenario disproved it. At 224 requests/second the backend logged 524 failed
 * n8n calls and opened three circuit breakers (`bvc-precios`, `precios-hoy`, `news-sentiment`).
 *
 * The cause is a CACHE STAMPEDE. Spring's `@Cacheable` over Caffeine does not coalesce concurrent
 * loaders by default: when an entry expires and hundreds of requests arrive in the same instant,
 * every one of them misses, and every one of them calls n8n, because none has finished populating
 * the entry yet. A cache makes sequential traffic cheap; it does nothing for a simultaneous burst
 * at the moment of expiry.
 *
 * That is a real production behaviour, not a test artefact — see the stampede entry in
 * docs/superpowers/decisions.md.
 *
 * THE RULE THAT REPLACES IT: an endpoint is safe to load-test only if the TRANSITIVE CLOSURE of
 * its use case's dependencies contains no `*Provider` gateway.
 *
 * "Transitive" is the part that matters, and getting it wrong cost a second round of 6 stray n8n
 * calls. A first attempt at this rule counted Provider references in each use case and found zero
 * in `EvaluateDiversificationUseCase` — correctly, because it holds none. It depends on
 * `GetPortfolioUseCase`, which holds two. Checking one level deep is not checking.
 *
 * Verified by reading every `private final` field and following each use-case dependency:
 *
 *   ListTransactionsUseCase        TransactionRepository                       → SAFE
 *   GetSignalsDashboardUseCase     TransactionRepository, SignalSnapshotRepo   → SAFE
 *   EvaluateDiversificationUseCase GetPortfolioUseCase → quotes + TRM          → NOT safe
 *   GetPortfolioUseCase            QuoteProvider, ExchangeRateProvider         → NOT safe
 *   GetTotalReturnUseCase          GetPortfolioUseCase + ExchangeRateProvider  → NOT safe
 *   GetAiSynthesisUseCase          AiSynthesisProvider                         → NOT safe
 *   GetEarningsUseCase             EarningsProvider                            → NOT safe
 *   GetWatchlistUseCase            WatchlistProvider                           → NOT safe
 *
 * Two endpoints survive. That is a small measurable surface, and it is the honest one: those two
 * are where this application's own code is the only thing being timed.
 * ------------------------------------------------------------------------------------------------
 */

/**
 * Safe to load-test at any concurrency: database only, no outbound call is reachable.
 *
 * These measure what this project controls — the domain logic, JPA and Hibernate, the connection
 * pool, JSON serialisation, Spring Security's JWT verification on every request. Numbers from
 * these endpoints are attributable to this codebase, which is what makes them worth reporting.
 *
 * Warm single-request baselines (2026-11-12): /transactions 12 ms, /signals 22 ms,
 * /diversification 33 ms.
 */
export const DB_ONLY_ENDPOINTS = [
  { path: '/api/v1/transactions', name: 'GET /transactions', source: 'db' },
  { path: '/api/v1/signals', name: 'GET /signals', source: 'db' },
]

/**
 * Can reach n8n, and therefore the third-party APIs behind it. Called ONCE each by the smoke
 * scenario to confirm they answer; never under load.
 *
 * Three costs, all observed rather than hypothesised:
 *   1. Traffic against the author's own n8n on a 2-CPU VPS with no swap.
 *   2. Quota on metered third-party APIs (Finnhub, FMP, Yahoo Finance, ApeWisdom, StockTwits,
 *      MiniMax) — some cost money per call.
 *   3. Opening the Resilience4j circuit breakers, after which users get degraded data for minutes.
 *      This happened twice during development: once from 34 sequential /fundamentals calls, and
 *      once from the stress scenario before this classification was corrected.
 */
export const UPSTREAM_ENDPOINTS = [
  { path: '/api/v1/portfolio', name: 'GET /portfolio', source: 'n8n (quotes+TRM, cached but stampedes)' },
  // Transitively upstream: EvaluateDiversificationUseCase depends on GetPortfolioUseCase, because
  // concentration weights need market values and market values need quotes. It LOOKS database-only
  // — it holds no Provider reference of its own — which is precisely why the rule below is about
  // the dependency closure and not about direct references.
  { path: '/api/v1/diversification', name: 'GET /diversification', source: 'n8n (via GetPortfolioUseCase)' },
  { path: '/api/v1/portfolio/returns', name: 'GET /portfolio/returns', source: 'n8n (portfolio + TRM)' },
  { path: '/api/v1/ai-synthesis', name: 'GET /ai-synthesis', source: 'n8n (uncached)' },
  { path: '/api/v1/watchlist', name: 'GET /watchlist', source: 'n8n (uncached)' },
  { path: '/api/v1/earnings', name: 'GET /earnings', source: 'n8n (uncached)' },
]

/**
 * Never called by any scenario, smoke included.
 *
 * `POST /signals/refresh` takes about 95 seconds, calling n8n for all 20 instruments, and holds a
 * single-flight guard: a second concurrent call is refused with HTTP 409 by design. Concurrency
 * here measures the guard, not the system.
 */
export const EXCLUDED_ENDPOINTS = [
  {
    path: '/api/v1/signals/refresh',
    name: 'POST /signals/refresh',
    reason: '~95 s; single-flight guard answers 409 on overlap',
  },
]
