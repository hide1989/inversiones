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
