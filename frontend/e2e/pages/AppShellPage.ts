import type { Locator, Page } from '@playwright/test'

/** The authenticated shell: header, nav, and the logout control every spec that logs out needs. */
export class AppShellPage {
  constructor(private readonly page: Page) {}

  logoutButton(): Locator {
    return this.page.getByRole('button', { name: 'Cerrar sesión' })
  }

  async logout(): Promise<void> {
    await this.logoutButton().click()
  }

  navLink(label: string): Locator {
    return this.page.getByRole('link', { name: label, exact: true })
  }
}
