import type { Locator, Page } from '@playwright/test'

export interface TransactionFormInput {
  ticker: string
  brokerName: string
  type: 'BUY' | 'SELL'
  quantity: string
  unitPrice: string
  currency: 'USD' | 'COP'
  fees?: string
  tradedAt: string
}

/** `/transacciones`: the list, and the shared create/edit form (`transaction-form`). */
export class TransaccionesPage {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/transacciones')
  }

  heading(): Locator {
    return this.page.getByRole('heading', { name: 'Transacciones', level: 1 })
  }

  newButton(): Locator {
    return this.page.getByRole('button', { name: 'Nueva transacción' })
  }

  form(): Locator {
    return this.page.getByTestId('transaction-form')
  }

  rowFor(id: number): Locator {
    return this.page.getByTestId(`transaction-row-${id}`)
  }

  editButtonFor(id: number): Locator {
    return this.rowFor(id).getByRole('button', { name: 'Editar' })
  }

  deleteButtonFor(id: number): Locator {
    return this.rowFor(id).getByRole('button', { name: 'Eliminar' })
  }

  deleteConfirmFor(id: number): Locator {
    return this.page.getByTestId(`delete-confirm-${id}`)
  }

  confirmDeleteButtonFor(id: number): Locator {
    return this.deleteConfirmFor(id).getByRole('button', { name: 'Confirmar' })
  }

  /** Fills every field the form exposes; `fees` is left blank when omitted, matching the view's own optional handling. */
  async fillForm(input: TransactionFormInput): Promise<void> {
    const form = this.form()
    await form.getByLabel('Ticker').fill(input.ticker)
    await form.getByLabel('Bróker').fill(input.brokerName)
    await form.getByLabel('Tipo').selectOption(input.type)
    await form.getByLabel('Divisa').selectOption(input.currency)
    await form.getByLabel('Cantidad').fill(input.quantity)
    await form.getByLabel('Precio unitario').fill(input.unitPrice)
    if (input.fees !== undefined) {
      await form.getByLabel('Comisiones').fill(input.fees)
    }
    await form.getByLabel('Fecha').fill(input.tradedAt)
  }

  async submitForm(): Promise<void> {
    await this.form().getByRole('button', { name: 'Guardar' }).click()
  }

  async cancelForm(): Promise<void> {
    await this.form().getByRole('button', { name: 'Cancelar' }).click()
  }
}
