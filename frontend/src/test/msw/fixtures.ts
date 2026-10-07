// Fixtures shaped exactly like the contract table in
// docs/superpowers/plans/2026-10-06-frontend-spa.md, used by MSW handlers
// in tests so no test depends on the live backend.

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

export const totalReturnUnavailable = {
  segments: [
    {
      segment: 'USD_ASSETS',
      invested: { amount: '2000.0000000000', currency: 'USD' },
      marketValue: { amount: '2449.6000000000', currency: 'USD' },
      assetGain: { amount: '449.6000000000', currency: 'USD' },
    },
  ],
  baseRate: { base: 'USD', quote: 'COP', rate: '3912.4000000000', rateDate: '2026-10-01' },
  currentRate: null,
  currencyEffectAvailable: false,
  exchangeEffect: null,
  assetGainInCop: null,
  totalReturnInCop: null,
}
