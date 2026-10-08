import { expect, type Locator, type Page } from '@playwright/test'

/** The public `/login` view: the only route an unauthenticated session can reach. */
export class LoginPage {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/login')
  }

  usernameInput(): Locator {
    return this.page.getByLabel('Usuario')
  }

  passwordInput(): Locator {
    return this.page.getByLabel('Contraseña')
  }

  submitButton(): Locator {
    return this.page.getByRole('button', { name: /iniciar sesión/i })
  }

  /** Renders the backend's own `detail` verbatim — byte-identical for a bad password and an unknown username. */
  errorAlert(): Locator {
    return this.page.getByRole('alert')
  }

  async login(username: string, password: string): Promise<void> {
    await this.usernameInput().fill(username)
    await this.passwordInput().fill(password)
    await this.submitButton().click()
  }

  /** The credential is public in this repo but must never arrive prefilled. */
  async expectFieldsEmpty(): Promise<void> {
    await expect(this.usernameInput()).toHaveValue('')
    await expect(this.passwordInput()).toHaveValue('')
  }
}
