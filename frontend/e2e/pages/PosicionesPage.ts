import type { Locator, Page } from '@playwright/test'

/** `/posiciones`: the positions dashboard plus the total-return panel it embeds. */
export class PosicionesPage {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/posiciones')
  }

  heading(): Locator {
    return this.page.getByRole('heading', { name: 'Posiciones', level: 1 })
  }

  rowFor(ticker: string, broker: string): Locator {
    return this.page.getByTestId(`position-row-${ticker}-${broker}`)
  }

  totalReturnPanel(): Locator {
    return this.page.getByTestId('total-return-panel')
  }

  /** Present whether the currency effect is available or not — its contents differ, the region does not disappear. */
  currencyEffectRegion(): Locator {
    return this.page.getByTestId('currency-effect-region')
  }
}
