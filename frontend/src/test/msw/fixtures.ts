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

// A derived daily history for QQQ — the real `/signals/history` capture
// for this exact ticker/range was an empty array (no snapshot persisted
// yet), so this invents five consecutive trading days trending toward
// the scored fixture's QQQ combined score of 8.40, to exercise the
// on-demand sparkline with a shape a real history would plausibly have.
export const validSignalHistoryResponse = [
  { ticker: 'QQQ', snapshotDate: '2026-10-02', scores: { TECHNICAL: '7.0', NEWS: '7.1', ANALYST: '7.5', INSIDER: '7.8', SOCIAL: '7.0', WSB: '7.6' } },
  { ticker: 'QQQ', snapshotDate: '2026-10-03', scores: { TECHNICAL: '7.4', NEWS: '7.3', ANALYST: '7.8', INSIDER: '8.0', SOCIAL: '7.2', WSB: '7.9' } },
  { ticker: 'QQQ', snapshotDate: '2026-10-04', scores: { TECHNICAL: '7.6', NEWS: '7.5', ANALYST: '8.2', INSIDER: '8.1', SOCIAL: '7.5', WSB: '8.2' } },
  { ticker: 'QQQ', snapshotDate: '2026-10-05', scores: { TECHNICAL: '7.9', NEWS: '7.7', ANALYST: '8.6', INSIDER: '8.3', SOCIAL: '7.7', WSB: '8.5' } },
  { ticker: 'QQQ', snapshotDate: '2026-10-06', scores: { TECHNICAL: '8.1', NEWS: '7.9', ANALYST: '9.0', INSIDER: '8.5', SOCIAL: '7.8', WSB: '8.8' } },
]
