import type { Locator, Page } from '@playwright/test'

/** `/senales`: the signals table, its sortable column headers, and the manual refresh control. */
export class SenalesPage {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/senales')
  }

  heading(): Locator {
    return this.page.getByRole('heading', { name: 'Señales', level: 1 })
  }

  /** A sortable column header is rendered as a `<button>` carrying the header text plus a glyph. */
  columnHeaderButton(headerText: string): Locator {
    return this.page.getByRole('button', { name: new RegExp(`^${headerText}`) })
  }

  rowTickers(): Locator {
    return this.page.locator('tbody tr td:first-child span.font-medium')
  }

  /**
   * By `data-testid`, not accessible name: the button's own text swaps
   * between "Actualizar señales" and "Actualizando…" while the refresh
   * is in flight, so a name-based locator would stop matching the
   * instant the click it is asserting on takes effect.
   */
  refreshButton(): Locator {
    return this.page.getByTestId('refresh-signals-button')
  }

  refreshStatus(): Locator {
    return this.page.getByRole('status')
  }
}
