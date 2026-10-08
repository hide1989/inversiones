// Fixtures shaped exactly like the contract table in
// docs/superpowers/plans/2026-10-06-frontend-spa.md, used by MSW handlers
// in tests so no test depends on the live backend.

export const validLoginResponse = {
  token: 'test-token-abc123',
  expiresAt: '2026-10-07T00:00:00Z',
  username: 'david',
}

// The backend returns a byte-identical body for a wrong password and an
// unknown username, to prevent username enumeration. One fixture represents
// both failure causes on purpose.
export const invalidLoginProblem = {
  type: 'https://api.inversiones.local/errors/unauthorized',
  title: 'Unauthorized',
  status: 401,
  detail: 'usuario o contraseña incorrectos',
  instance: '/api/v1/auth/login',
}

export const validPortfolioResponse = {
  positions: [
    {
      ticker: 'QQQ',
      brokerName: 'Hapi',
      quantity: '4.0000000000',
      totalCost: { amount: '2000.0000000000', currency: 'USD' },
      averageCost: { amount: '500.0000000000', currency: 'USD' },
      realizedPnl: { amount: '0.0000000000', currency: 'USD' },
      marketValue: { amount: '2449.6000000000', currency: 'USD' },
      unrealizedPnl: { amount: '449.6000000000', currency: 'USD' },
      unrealizedPnlPercent: '22.48',
      dayChangePercent: '0.82',
      priced: true,
      costInBaseCurrency: { amount: '7820000.0000000000', currency: 'COP' },
      marketValueInBaseCurrency: { amount: '9582784.0000000000', currency: 'COP' },
    },
  ],
  totalInvested: { amount: '2463.0000000000', currency: 'USD' },
  totalMarketValue: { amount: '2791.4200000000', currency: 'USD' },
  totalUnrealizedPnl: { amount: '328.4200000000', currency: 'USD' },
  unpricedTickers: [],
  baseCurrency: 'COP',
}

// Captured straight from the running backend's GET /portfolio/returns
// (see .superpowers/sdd/2026-10-06-frontend-spa/task-7-report.md) rather
// than hand-written, so the panel's tests exercise the real contract
// shape and the live figures the plan calls out: an asset gain of
// +1,799,360.10 COP against an exchange effect of -1,310,802.48 COP.
export const validTotalReturnResponse = {
  segments: [
    {
      segment: 'USD_ASSETS',
      invested: { amount: '2913.2899999999', currency: 'USD' },
      marketValue: { amount: '3151.7492650608', currency: 'USD' },
      assetGain: { amount: '238.4592650609', currency: 'USD' },
    },
    {
      segment: 'TRII_ADR',
      invested: { amount: '640.5200000000', currency: 'USD' },
      marketValue: { amount: '894.9800000000', currency: 'USD' },
      assetGain: { amount: '254.4600000000', currency: 'USD' },
    },
    {
      segment: 'BVC_COLOMBIA',
      invested: { amount: '840272.4100000000', currency: 'COP' },
      marketValue: { amount: '1062500.0000000000', currency: 'COP' },
      assetGain: { amount: '222227.5900000000', currency: 'COP' },
    },
  ],
  baseRate: { base: 'USD', quote: 'COP', rate: '3568.42', rateDate: '2026-10-07' },
  currentRate: { base: 'USD', quote: 'COP', rate: '3199.575723', rateDate: '2026-10-07' },
  currencyEffectAvailable: true,
  exchangeEffect: { amount: '-1310802.4800453331', currency: 'COP' },
  assetGainInCop: { amount: '1799360.1038878578', currency: 'COP' },
  totalReturnInCop: { amount: '488557.6238425247', currency: 'COP' },
}

// A derived variant of the real fixture above, not a hand-rolled one: the
// backend returns `currencyEffectAvailable: false` with the rate and the
// three effect fields null only when its exchange-rate source is down.
// `currentRate` is currently reachable, so this case is constructed by
// nulling exactly the fields the backend would null, same as Task 6's
// unpriced-position variant of its own real portfolio fixture.
export const totalReturnUnavailable = {
  ...validTotalReturnResponse,
  currentRate: null,
  currencyEffectAvailable: false,
  exchangeEffect: null,
  assetGainInCop: null,
  totalReturnInCop: null,
}

// Segment-only variants for the peso-only / dollar-only rendering tests —
// same baseRate/currentRate, a subset of the real segments.
export const totalReturnPesoOnly = {
  ...validTotalReturnResponse,
  segments: [
    {
      segment: 'BVC_COLOMBIA',
      invested: { amount: '840272.4100000000', currency: 'COP' },
      marketValue: { amount: '1062500.0000000000', currency: 'COP' },
      assetGain: { amount: '222227.5900000000', currency: 'COP' },
    },
  ],
}

export const totalReturnDollarOnly = {
  ...validTotalReturnResponse,
  segments: [
    {
      segment: 'USD_ASSETS',
      invested: { amount: '2913.2899999999', currency: 'USD' },
      marketValue: { amount: '3151.7492650608', currency: 'USD' },
      assetGain: { amount: '238.4592650609', currency: 'USD' },
    },
    {
      segment: 'TRII_ADR',
      invested: { amount: '640.5200000000', currency: 'USD' },
      marketValue: { amount: '894.9800000000', currency: 'USD' },
      assetGain: { amount: '254.4600000000', currency: 'USD' },
    },
  ],
}

// Captured verbatim from the running backend's GET /signals (see
// .superpowers/sdd/2026-10-06-frontend-spa/task-8-report.md): the
// scheduled refresh had not run yet, so every one of the twenty seeded
// tickers has an empty snapshot dated the epoch and zero `usedSources`.
// The backend still returns `score.value: "0.00"` / `score.level:
// "STRONG_SELL"` for each — its threshold table applied to a zero-source
// score — which is exactly the trap this fixture exists to exercise: the
// UI must read `usedSources.length === 0`, never the value, to tell "no
// assessment" apart from "a strong sell".
const NO_DATA_TICKERS = [
  'QQQ', 'VOO', 'SMH', 'GLD', 'TSLA', 'GOOGL', 'TTWO', 'IBIT', 'NU', 'TSM',
  'AMZN', 'MSFT', 'NLR', 'SPCX', 'PLTR', 'UNH', 'JPM', 'EC', 'TERPEL', 'MINEROS',
]

function noDataSignal(ticker: string) {
  return {
    ticker,
    snapshot: { ticker, snapshotDate: '1970-01-01', scores: {} },
    score: { value: '0.00', level: 'STRONG_SELL', usedSources: [] },
  }
}

export const validSignalsNoDataResponse = {
  signals: NO_DATA_TICKERS.map(noDataSignal),
  averageScore: '0.00',
  levelCounts: {},
  tickersWithoutData: [...NO_DATA_TICKERS],
}

// `GET /signals/history` for the real no-data capture: no snapshot has
// ever been persisted for this ticker yet, so the backend returns an
// empty array rather than synthesising history.
export const emptySignalHistoryResponse: unknown[] = []

// A derived scored variant — there is no real fixture with actual scores
// yet (the capture above is the live state), so this is hand-built to
// exercise the normal path. Values invented, and why each is plausible:
// - Five tickers (QQQ, MSFT, AMZN, TSLA, GOOGL) get a combined score and a
//   subset of the six sources (3 to 6 of them), mirroring the real
//   contract's six source keys (TECHNICAL, NEWS, ANALYST, INSIDER,
//   SOCIAL, WSB) and the fact that not every source covers every ticker.
// - Per-source values sit inside the 0-10 scale `ScoreBar` already
//   assumes (see its own test), roughly clustered around each ticker's
//   combined score the way a weighted average would look, without this
//   fixture performing that arithmetic itself (fixtures are authored
//   data, not browser computation).
// - Levels (STRONG_BUY, BUY, HOLD, SELL) follow a plausible threshold
//   table descending with score, consistent with the one real level this
//   plan observed (STRONG_SELL at 0.00).
// - GLD is kept as a sixth, still-no-data ticker in the same response, so
//   the scored fixture also exercises a dashboard that mixes real
//   assessments with a no-data row — the case the real capture alone
//   cannot show.
// - `averageScore` and `levelCounts` are given as the backend would
//   compute them: averaged and counted over the five scored tickers
//   only, excluding GLD — matching the documented rule that no-data
//   tickers are excluded from both.
// - The array is already in combined-score-descending order, matching
//   the API's own documented ordering.
export const validSignalsScoredResponse = {
  signals: [
    {
      ticker: 'QQQ',
      snapshot: {
        ticker: 'QQQ',
        snapshotDate: '2026-10-06',
        scores: {
          TECHNICAL: '8.1', NEWS: '7.9', ANALYST: '9.0',
          INSIDER: '8.5', SOCIAL: '7.8', WSB: '8.8',
        },
      },
      score: { value: '8.40', level: 'STRONG_BUY', usedSources: ['TECHNICAL', 'NEWS', 'ANALYST', 'INSIDER', 'SOCIAL', 'WSB'] },
    },
    {
      ticker: 'MSFT',
      snapshot: {
        ticker: 'MSFT',
        snapshotDate: '2026-10-06',
        scores: { TECHNICAL: '7.5', NEWS: '6.8', ANALYST: '7.9', SOCIAL: '6.5', WSB: '7.3' },
      },
      score: { value: '7.20', level: 'BUY', usedSources: ['TECHNICAL', 'NEWS', 'ANALYST', 'SOCIAL', 'WSB'] },
    },
    {
      ticker: 'AMZN',
      snapshot: {
        ticker: 'AMZN',
        snapshotDate: '2026-10-06',
        scores: { TECHNICAL: '6.0', NEWS: '5.8', ANALYST: '6.9', WSB: '5.6' },
      },
      score: { value: '6.10', level: 'BUY', usedSources: ['TECHNICAL', 'NEWS', 'ANALYST', 'WSB'] },
    },
    {
      ticker: 'TSLA',
      snapshot: {
        ticker: 'TSLA',
        snapshotDate: '2026-10-06',
        scores: { TECHNICAL: '5.2', NEWS: '3.9', WSB: '5.1' },
      },
      score: { value: '4.80', level: 'HOLD', usedSources: ['TECHNICAL', 'NEWS', 'WSB'] },
    },
    {
      ticker: 'GOOGL',
      snapshot: {
        ticker: 'GOOGL',
        snapshotDate: '2026-10-06',
        scores: { TECHNICAL: '3.5', ANALYST: '2.8', INSIDER: '3.0' },
      },
      score: { value: '3.10', level: 'SELL', usedSources: ['TECHNICAL', 'ANALYST', 'INSIDER'] },
    },
    noDataSignal('GLD'),
  ],
  averageScore: '5.92',
  levelCounts: { STRONG_BUY: 1, BUY: 2, HOLD: 1, SELL: 1 },
  tickersWithoutData: ['GLD'],
}

// Captured verbatim from the running backend's GET /diversification (see
// .superpowers/sdd/2026-10-06-frontend-spa/task-10-report.md): the live
// portfolio is currently compliant with zero violations, so this is the
// only shape a live capture can prove — the violation-rendering tests
// build their own fixture on top of this one.
export const validDiversificationResponse = {
  violations: [],
  totalMarketValue: { amount: '4340.6991515628', currency: 'USD' },
  weightByTicker: {
    TERPEL: '3.4417',
    SMH: '7.7257',
    NU: '4.8579',
    TTWO: '4.8471',
    TSLA: '2.0155',
    JPM: '1.1565',
    QQQ: '10.8854',
    MINEROS: '4.0537',
    GOOGL: '5.0849',
    IBIT: '2.9395',
    NLR: '1.0456',
    AMZN: '10.0455',
    PLTR: '0.7446',
    MSFT: '3.5952',
    EC: '19.1559',
    VOO: '5.1539',
    GLD: '2.0760',
    TSM: '6.3886',
    UNH: '1.0353',
    SPCX: '3.7515',
  },
  weightBySector: {
    Materials: '6.1297',
    'Consumer Discretionary': '12.0610',
    Industrials: '3.7515',
    'Health Care': '1.0353',
    'Broad Market': '18.9789',
    Financials: '6.0144',
    'Communication Services': '9.9320',
    Energy: '23.6432',
    Technology: '18.4541',
  },
  compliant: true,
}

// A derived daily history for QQQ — the real `/signals/history` capture
// for this exact ticker/range was an empty array (no snapshot persisted
// yet), so this invents five consecutive trading days trending toward
// the scored fixture's QQQ combined score of 8.40, to exercise the
// on-demand sparkline with a shape a real history would plausibly have.
// Captured verbatim from the running backend's GET /ai-synthesis: the
// scheduled AI synthesis job had not run yet, so every one of the 15
// seeded tickers is in `withoutData` and `averageScore` is the backend's
// "0.00" computed over zero scored tickers — meaningless, not a real
// score. This is the shape the view must defend against: a `0` that
// means "absent" must never reach the DOM as if it meant "measured".
export const validAiSynthesisEmptyResponse = {
  withData: [],
  withoutData: [
    'AMZN', 'EC', 'GLD', 'GOOGL', 'IBIT', 'MSFT', 'NU', 'NLR', 'QQQ',
    'SMH', 'SPCX', 'TSLA', 'TSM', 'TTWO', 'VOO',
  ],
  averageScore: '0.00',
}

// A derived variant with real data for some tickers — there is no live
// capture with any `withData` entries yet (the capture above is the live
// state), so this is hand-built to exercise the normal rendering path:
// two scored tickers and three left in `withoutData`, with `averageScore`
// authored as what the backend would compute over the two scored tickers
// only (never computed by this fixture, let alone by the view).
export const validAiSynthesisWithDataResponse = {
  withData: [
    {
      ticker: 'AAPL',
      snapshotDate: '2026-10-06',
      iaScore: '8.20',
      signal: 'BUY',
      conviction: 'HIGH',
      horizon: 'MEDIUM_TERM',
      summary:
        'El impulso en servicios y la expansión de márgenes sostienen una tesis constructiva de mediano plazo.',
      catalysts: ['Lanzamiento de nuevo hardware', 'Crecimiento en servicios'],
      risks: ['Desaceleración en China', 'Presión regulatoria en la App Store'],
    },
    {
      ticker: 'MSFT',
      snapshotDate: '2026-10-06',
      iaScore: '7.40',
      signal: 'HOLD',
      conviction: 'MEDIUM',
      horizon: 'LONG_TERM',
      summary: 'El crecimiento en la nube compensa la desaceleración en licenciamiento tradicional.',
      catalysts: ['Adopción de Copilot'],
      risks: ['Competencia en infraestructura cloud'],
    },
  ],
  withoutData: ['AMZN', 'GLD', 'NU'],
  averageScore: '7.80',
}

// Final review Critical 2: AiSynthesis guards only ticker and
// snapshotDate; iaScore, signal, conviction, horizon and summary are all
// unguarded BigDecimal/String fields that DecimalStrings.of / a bare
// pass-through can emit as JSON null. This entry puts a null in EACH of
// the five fields across two withData rows (never the same row twice),
// so each field's no-data path is independently exercised — a schema
// without .nullable() on all five would reject this whole response, the
// same way earnings.ts did.
export const validAiSynthesisWithNullFieldsResponse = {
  withData: [
    {
      ticker: 'AAPL',
      snapshotDate: '2026-10-06',
      iaScore: null,
      signal: 'BUY',
      conviction: null,
      horizon: 'MEDIUM_TERM',
      summary: 'El impulso en servicios sostiene una tesis constructiva.',
      catalysts: [],
      risks: [],
    },
    {
      ticker: 'MSFT',
      snapshotDate: '2026-10-06',
      iaScore: '7.40',
      signal: null,
      conviction: 'MEDIUM',
      horizon: null,
      summary: null,
      catalysts: [],
      risks: [],
    },
  ],
  withoutData: [],
  averageScore: '7.40',
}

// Captured verbatim from the running backend's GET /fundamentals/NU: a
// stock (isEtf: false) with hasData: true but three metrics null, plus
// float-artifact decimal strings (roe, peg, beta) the view must route
// through formatDecimalString rather than display verbatim.
export const validFundamentalsStockResponse = {
  ticker: 'NU',
  isEtf: false,
  hasData: true,
  per: '20.9712',
  peg: '0.3662433623319399',
  priceToBook: '7.066',
  priceToSales: '3.9114',
  evToEbitda: '25.9002',
  roe: '30.259999999999998',
  netMargin: '18.65',
  revenueCagr5y: null,
  epsCagr5y: null,
  debtToEquity: '4.1747',
  beta: '0.96430856',
  dividendYield: null,
  currentRatio: '0.1973',
}

// Captured verbatim from the running backend's GET /fundamentals/QQQ: an
// ETF (isEtf: true) with hasData: true, yet eleven of the twelve
// nullable metrics are null — only `beta` is present. This is the exact
// shape that must NOT drive a P/E-grid render just because hasData is
// true: `isEtf` picks which multiples are meaningful, and per-metric
// presence decides what actually renders.
export const validFundamentalsEtfResponse = {
  ticker: 'QQQ',
  isEtf: true,
  hasData: true,
  per: null,
  peg: null,
  priceToBook: null,
  priceToSales: null,
  evToEbitda: null,
  roe: null,
  netMargin: null,
  revenueCagr5y: null,
  epsCagr5y: null,
  debtToEquity: null,
  beta: '1.2417293',
  dividendYield: null,
  currentRatio: null,
}

// No fundamentals exist for this ticker at all — `hasData: false`, every
// nullable metric null. Not a live capture (no seeded ticker returned
// this) and `beta` was never observed null in either live sample
// (NU or QQQ), so the schema keeps it required — this fixture respects
// that by giving it a placeholder value rather than asserting a null
// shape the backend has not actually been seen to send.
export const fundamentalsNoDataResponse = {
  ticker: 'NOPE',
  isEtf: false,
  hasData: false,
  per: null,
  peg: null,
  priceToBook: null,
  priceToSales: null,
  evToEbitda: null,
  roe: null,
  netMargin: null,
  revenueCagr5y: null,
  epsCagr5y: null,
  debtToEquity: null,
  beta: '0.0000000',
  dividendYield: null,
  currentRatio: null,
}

// Captured verbatim from the running backend's GET /watchlist: 31 items,
// 12 of them null for both `price` and `technicalScore` (the DTO's
// documented "frequently absent upstream" fields), using the Spanish
// COMPRA/FUERTE COMPRA/MANTENER/VENTA signal vocabulary that is specific
// to this endpoint and distinct from the rest of the contract.
export const validWatchlistResponse = [
  { ticker: 'SPY', price: null, technicalScore: null, wsbScore: '9', combinedScore: '9', signal: 'COMPRA' },
  { ticker: 'AMD', price: '649.42', technicalScore: '8', wsbScore: '9.5', combinedScore: '8.5', signal: 'FUERTE COMPRA' },
  { ticker: 'MU', price: null, technicalScore: null, wsbScore: '8.5', combinedScore: '8.5', signal: 'COMPRA' },
  { ticker: 'HTZ', price: null, technicalScore: null, wsbScore: '8.5', combinedScore: '8.5', signal: 'COMPRA' },
  { ticker: 'WDC', price: null, technicalScore: null, wsbScore: '8.5', combinedScore: '8.5', signal: 'COMPRA' },
  { ticker: 'MRVL', price: null, technicalScore: null, wsbScore: '8.5', combinedScore: '8.5', signal: 'COMPRA' },
  { ticker: 'BULL', price: null, technicalScore: null, wsbScore: '8.5', combinedScore: '8.5', signal: 'COMPRA' },
  { ticker: 'PENG', price: null, technicalScore: null, wsbScore: '8.5', combinedScore: '8.5', signal: 'COMPRA' },
  { ticker: 'NVDA', price: '239.24', technicalScore: '8', wsbScore: '8.5', combinedScore: '8.1', signal: 'FUERTE COMPRA' },
  { ticker: 'AAPL', price: '333.63', technicalScore: '8', wsbScore: '8.5', combinedScore: '8.1', signal: 'FUERTE COMPRA' },
  { ticker: 'SNDK', price: null, technicalScore: null, wsbScore: '8', combinedScore: '8', signal: 'COMPRA' },
  { ticker: 'AVGO', price: '375.81', technicalScore: '7.5', wsbScore: '8.5', combinedScore: '7.8', signal: 'COMPRA' },
  { ticker: 'DTE', price: null, technicalScore: null, wsbScore: '7.5', combinedScore: '7.5', signal: 'COMPRA' },
  { ticker: 'INTC', price: null, technicalScore: null, wsbScore: '7.5', combinedScore: '7.5', signal: 'COMPRA' },
  { ticker: 'RKLB', price: null, technicalScore: null, wsbScore: '7.5', combinedScore: '7.5', signal: 'COMPRA' },
  { ticker: 'ABBV', price: '266.69', technicalScore: '8', wsbScore: '5', combinedScore: '7.1', signal: 'COMPRA' },
  { ticker: 'CVX', price: '207.58', technicalScore: '8', wsbScore: '5', combinedScore: '7.1', signal: 'COMPRA' },
  { ticker: 'VTI', price: '382.54', technicalScore: '8', wsbScore: '5', combinedScore: '7.1', signal: 'COMPRA' },
  { ticker: 'VST', price: null, technicalScore: null, wsbScore: '7', combinedScore: '7', signal: 'COMPRA' },
  { ticker: 'PLTR', price: '192.07', technicalScore: '7', wsbScore: '6.5', combinedScore: '6.9', signal: 'COMPRA' },
  { ticker: 'QCOM', price: '181.03', technicalScore: '7', wsbScore: '5', combinedScore: '6.4', signal: 'MANTENER' },
  { ticker: 'META', price: '738.88', technicalScore: '7', wsbScore: '5', combinedScore: '6.4', signal: 'MANTENER' },
  { ticker: 'CRM', price: '224.99', technicalScore: '7', wsbScore: '5', combinedScore: '6.4', signal: 'MANTENER' },
  { ticker: 'V', price: '370.64', technicalScore: '7', wsbScore: '5', combinedScore: '6.4', signal: 'MANTENER' },
  { ticker: 'XOM', price: '164.48', technicalScore: '7', wsbScore: '5', combinedScore: '6.4', signal: 'MANTENER' },
  { ticker: 'SKM', price: '36.45', technicalScore: '7', wsbScore: '5', combinedScore: '6.4', signal: 'MANTENER' },
  { ticker: 'NFLX', price: '68.69', technicalScore: '3.5', wsbScore: '6.5', combinedScore: '4.4', signal: 'VENTA' },
  { ticker: 'LLY', price: '1157.49', technicalScore: '4', wsbScore: '5.5', combinedScore: '4.4', signal: 'VENTA' },
  { ticker: 'JPM', price: '331.28', technicalScore: '4', wsbScore: '5', combinedScore: '4.3', signal: 'VENTA' },
  { ticker: 'MA', price: '566.58', technicalScore: '4', wsbScore: '5', combinedScore: '4.3', signal: 'VENTA' },
  { ticker: 'UNH', price: '376.32', technicalScore: '4', wsbScore: '5', combinedScore: '4.3', signal: 'VENTA' },
]

// GET /earnings currently returns `[]` live (no earnings events seeded),
// so "ordered by proximity" cannot be confirmed against the backend and
// must be proven here. Deliberately NOT in days-remaining order — MSFT
// (30 days out) comes before AAPL (5 days out) — so a proximity-ordering
// assertion against this fixture only passes if the view actually sorts;
// it would pass against a do-nothing implementation if the fixture were
// already ordered.
export const earningsNotInProximityOrder = [
  { ticker: 'MSFT', reportDate: '2026-11-06', daysRemaining: 30, estimatedEps: '3.12', historicalSurprise: '2.4' },
  { ticker: 'AAPL', reportDate: '2026-10-12', daysRemaining: 5, estimatedEps: '1.85', historicalSurprise: '-1.1' },
  { ticker: 'NVDA', reportDate: '2026-10-20', daysRemaining: 13, estimatedEps: '0.98', historicalSurprise: '5.6' },
]

// Final review Critical 1: EarningsEvent's estimatedEps and
// historicalSurprise are unguarded BigDecimal fields in the Java model —
// DecimalStrings.of(null) emits a JSON null for either whenever n8n's
// proximo_reporte is set but eps_estimado or sorpresa_promedio_pct is
// absent. This fixture puts a null in EACH field (not the same row, so
// both code paths are independently exercised) to prove the view renders
// a no-data cell per field instead of rejecting the whole array.
export const earningsWithNullMetrics = [
  { ticker: 'MSFT', reportDate: '2026-11-06', daysRemaining: 30, estimatedEps: null, historicalSurprise: '2.4' },
  { ticker: 'AAPL', reportDate: '2026-10-12', daysRemaining: 5, estimatedEps: '1.85', historicalSurprise: null },
]

export const validSignalHistoryResponse = [
  { ticker: 'QQQ', snapshotDate: '2026-10-02', scores: { TECHNICAL: '7.0', NEWS: '7.1', ANALYST: '7.5', INSIDER: '7.8', SOCIAL: '7.0', WSB: '7.6' } },
  { ticker: 'QQQ', snapshotDate: '2026-10-03', scores: { TECHNICAL: '7.4', NEWS: '7.3', ANALYST: '7.8', INSIDER: '8.0', SOCIAL: '7.2', WSB: '7.9' } },
  { ticker: 'QQQ', snapshotDate: '2026-10-04', scores: { TECHNICAL: '7.6', NEWS: '7.5', ANALYST: '8.2', INSIDER: '8.1', SOCIAL: '7.5', WSB: '8.2' } },
  { ticker: 'QQQ', snapshotDate: '2026-10-05', scores: { TECHNICAL: '7.9', NEWS: '7.7', ANALYST: '8.6', INSIDER: '8.3', SOCIAL: '7.7', WSB: '8.5' } },
  { ticker: 'QQQ', snapshotDate: '2026-10-06', scores: { TECHNICAL: '8.1', NEWS: '7.9', ANALYST: '9.0', INSIDER: '8.5', SOCIAL: '7.8', WSB: '8.8' } },
]
