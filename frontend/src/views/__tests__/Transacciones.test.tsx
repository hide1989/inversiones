import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { server } from '../../test/msw/server'
import { portfolioKeys } from '../../api/queries/portfolio'
import { returnsKeys } from '../../api/queries/returns'
import { diversificationKeys } from '../../api/queries/diversification'
import { Transacciones } from '../Transacciones'

const API_BASE = 'http://localhost:8080/api/v1'

const txQqq = {
  id: 1,
  ticker: 'QQQ',
  brokerName: 'Hapi',
  type: 'BUY',
  quantity: '0.4863720000',
  unitPrice: { amount: '616.8118230490', currency: 'USD' },
  fees: { amount: '0.0000000000', currency: 'USD' },
  tradedAt: '2026-06-30',
  grossAmount: { amount: '300.0000000000', currency: 'USD' },
  netAmount: { amount: '300.0000000000', currency: 'USD' },
}

const txVoo = {
  id: 2,
  ticker: 'VOO',
  brokerName: 'Trii',
  type: 'SELL',
  quantity: '1.0000000000',
  unitPrice: { amount: '100.0000000000', currency: 'USD' },
  fees: { amount: '1.0000000000', currency: 'USD' },
  tradedAt: '2026-05-01',
  grossAmount: { amount: '100.0000000000', currency: 'USD' },
  netAmount: { amount: '99.0000000000', currency: 'USD' },
}

let capturedListUrls: string[] = []

function mockList(body: unknown[] = [txQqq, txVoo]) {
  capturedListUrls = []
  server.use(
    http.get(`${API_BASE}/transactions`, ({ request }) => {
      capturedListUrls.push(request.url)
      return HttpResponse.json(body)
    }),
  )
}

function mockCreate(responder: (body: Record<string, unknown>) => Response) {
  server.use(
    http.post(`${API_BASE}/transactions`, async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>
      return responder(body)
    }),
  )
}

function mockUpdate(responder: (id: string, body: Record<string, unknown>) => Response) {
  server.use(
    http.put(`${API_BASE}/transactions/:id`, async ({ request, params }) => {
      const body = (await request.json()) as Record<string, unknown>
      return responder(String(params.id), body)
    }),
  )
}

let deleteCallCount = 0
let lastDeletedId: string | null = null

function mockDelete() {
  deleteCallCount = 0
  lastDeletedId = null
  server.use(
    http.delete(`${API_BASE}/transactions/:id`, ({ params }) => {
      deleteCallCount += 1
      lastDeletedId = String(params.id)
      return new HttpResponse(null, { status: 204 })
    }),
  )
}

function renderView(client?: QueryClient) {
  const queryClient = client ?? new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return { ...render(
    <QueryClientProvider client={queryClient}>
      <Transacciones />
    </QueryClientProvider>,
  ), queryClient }
}

async function openCreateForm(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('button', { name: /nueva transacción/i }))
  return screen.getByTestId('transaction-form')
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('the transactions list', () => {
  it('renders rows from GET /transactions', async () => {
    mockList()
    renderView()

    const qqqRow = await screen.findByTestId('transaction-row-1')
    expect(within(qqqRow).getByText('QQQ')).toBeInTheDocument()
    expect(within(qqqRow).getByText('Hapi')).toBeInTheDocument()
    expect(within(qqqRow).getByText('0.486372')).toBeInTheDocument()

    const vooRow = screen.getByTestId('transaction-row-2')
    expect(within(vooRow).getByText('VOO')).toBeInTheDocument()
  })

  it('shows the empty state for no transactions', async () => {
    mockList([])
    renderView()
    expect(await screen.findByText(/no hay transacciones todavía/i)).toBeInTheDocument()
  })

  it('shows the error state with the server message and a retry affordance', async () => {
    server.use(
      http.get(`${API_BASE}/transactions`, () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Error', status: 500, detail: 'Fallo al listar transacciones.', instance: '/api/v1/transactions' },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    renderView()
    expect(await screen.findByText('Fallo al listar transacciones.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reintentar/i })).toBeInTheDocument()
  })
})

describe('filters', () => {
  it('sends the ticker filter as a query parameter rather than filtering client-side', async () => {
    mockList()
    const user = userEvent.setup()
    renderView()

    await screen.findByTestId('transaction-row-1')
    expect(new URL(capturedListUrls[capturedListUrls.length - 1] ?? '').searchParams.get('ticker')).toBeNull()

    await user.type(screen.getByLabelText('Ticker', { selector: 'input' }), 'QQQ')

    await waitFor(() => {
      const last = new URL(capturedListUrls[capturedListUrls.length - 1] ?? '')
      expect(last.searchParams.get('ticker')).toBe('QQQ')
    })
  })

  it('sends the broker filter as a query parameter', async () => {
    mockList()
    const user = userEvent.setup()
    renderView()

    await screen.findByTestId('transaction-row-1')
    await user.type(screen.getByLabelText('Bróker', { selector: 'input' }), 'Hapi')

    await waitFor(() => {
      const last = new URL(capturedListUrls[capturedListUrls.length - 1] ?? '')
      expect(last.searchParams.get('brokerName')).toBe('Hapi')
    })
  })

  it('sends the date range filters as "from" and "to" query parameters', async () => {
    mockList()
    renderView()

    await screen.findByTestId('transaction-row-1')
    const fromInput = screen.getByLabelText('Desde')
    const toInput = screen.getByLabelText('Hasta')

    fromInput.focus()
    await userEvent.setup().clear(fromInput)
    // Date inputs resist userEvent.type reliably across jsdom versions; a direct value
    // assignment followed by firing the change event is the same mechanism userEvent
    // itself uses under the hood for a native date input.
    const nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set
    nativeInputValueSetter?.call(fromInput, '2026-01-01')
    fromInput.dispatchEvent(new Event('input', { bubbles: true }))
    nativeInputValueSetter?.call(toInput, '2026-12-31')
    toInput.dispatchEvent(new Event('input', { bubbles: true }))

    await waitFor(() => {
      const last = new URL(capturedListUrls[capturedListUrls.length - 1] ?? '')
      expect(last.searchParams.get('from')).toBe('2026-01-01')
      expect(last.searchParams.get('to')).toBe('2026-12-31')
    })
  })
})

describe('creating a transaction', () => {
  it('posts the exact decimal strings unchanged and invalidates transactions, portfolio, returns and diversification', async () => {
    mockList()
    const user = userEvent.setup()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    // portfolio/returns/diversification are never fetched by this view, so — unlike the
    // transactions list, which gets populated by the view's own GET below — their cache
    // entries must be seeded here for invalidateQueries to have anything to mark.
    queryClient.setQueryData(portfolioKeys.all, { seeded: true })
    queryClient.setQueryData(returnsKeys.all, { seeded: true })
    queryClient.setQueryData(diversificationKeys.all, { seeded: true })
    renderView(queryClient)

    await screen.findByTestId('transaction-row-1')

    const captured: { body: Record<string, unknown> | null } = { body: null }
    server.use(
      http.post(`${API_BASE}/transactions`, async ({ request }) => {
        captured.body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...txQqq, id: 3 }, { status: 201 })
      }),
    )

    const form = await openCreateForm(user)
    await user.type(within(form).getByLabelText('Ticker'), 'QQQ')
    await user.type(within(form).getByLabelText('Bróker'), 'Hapi')
    await user.type(within(form).getByLabelText('Cantidad'), '0.4863720000')
    await user.type(within(form).getByLabelText('Precio unitario'), '616.8118230490')
    await user.type(within(form).getByLabelText('Fecha'), '2026-06-30')
    // Captured before submitting: the transactions list is actively observed by
    // this mounted view, so its invalidation triggers an automatic refetch almost
    // immediately — a baseline taken any later could already include it.
    const listRequestsBeforeSubmit = capturedListUrls.length
    await user.click(within(form).getByRole('button', { name: /^guardar$/i }))

    await waitFor(() => expect(captured.body).not.toBeNull())
    // The exact string the user typed, byte for byte — no Number()/parseFloat ever
    // touched it. A float round-trip would have produced "0.486372" with a different
    // internal representation, or silently dropped trailing/leading structure; this
    // assertion is on the literal string the backend receives over the wire.
    expect(captured.body?.quantity).toBe('0.4863720000')
    expect(captured.body?.unitPrice).toBe('616.8118230490')
    expect(captured.body?.fees).toBeUndefined()

    // By the time a `waitFor` could read the transactions query's `isInvalidated`
    // flag, its automatic refetch (it is actively observed) may have already
    // flipped it back to false. A second GET on the same endpoint is the
    // observable proof of that invalidation instead.
    await waitFor(() => {
      expect(capturedListUrls.length).toBeGreaterThan(listRequestsBeforeSubmit)
    })

    // portfolio/returns/diversification have no active observer anywhere in this
    // view, so an invalidated entry simply stays marked invalidated (nothing
    // refetches it) — isInvalidated is a stable, directly assertable signal here.
    await waitFor(() => {
      expect(queryClient.getQueryState(portfolioKeys.all)?.isInvalidated).toBe(true)
      expect(queryClient.getQueryState(returnsKeys.all)?.isInvalidated).toBe(true)
      expect(queryClient.getQueryState(diversificationKeys.all)?.isInvalidated).toBe(true)
    })
  })

  it('renders the server ProblemDetail on a 400 validation failure', async () => {
    mockList()
    const user = userEvent.setup()
    renderView()

    await screen.findByTestId('transaction-row-1')
    mockCreate(() =>
      HttpResponse.json(
        {
          type: 'about:blank',
          title: 'Bad Request',
          status: 400,
          detail: 'quantity must be a positive decimal string',
          instance: '/api/v1/transactions',
        },
        { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )

    const form = await openCreateForm(user)
    await user.type(within(form).getByLabelText('Ticker'), 'QQQ')
    await user.type(within(form).getByLabelText('Bróker'), 'Hapi')
    await user.type(within(form).getByLabelText('Cantidad'), '1')
    await user.type(within(form).getByLabelText('Precio unitario'), '1')
    await user.type(within(form).getByLabelText('Fecha'), '2026-06-30')
    await user.click(within(form).getByRole('button', { name: /^guardar$/i }))

    expect(await screen.findByText('quantity must be a positive decimal string')).toBeInTheDocument()
  })

  it('renders amount and quantity inputs as type="text", never type="number"', async () => {
    mockList()
    const user = userEvent.setup()
    renderView()

    await screen.findByTestId('transaction-row-1')
    const form = await openCreateForm(user)

    expect(within(form).getByLabelText('Cantidad')).toHaveAttribute('type', 'text')
    expect(within(form).getByLabelText('Precio unitario')).toHaveAttribute('type', 'text')
    expect(within(form).getByLabelText('Comisiones')).toHaveAttribute('type', 'text')
  })

  // Final review Important 4: before this fix, submitting an empty form left
  // focus on Guardar with a grey line of text that read as help, and a
  // screen reader announced nothing — the client-side validation errors
  // never set aria-invalid, never used role="alert", and were never
  // distinguishable from hint="Opcional" on the fees field.
  it('marks every invalid field aria-invalid, announces each error via role="alert", and moves focus to the first invalid field', async () => {
    mockList()
    const user = userEvent.setup()
    renderView()

    await screen.findByTestId('transaction-row-1')
    const form = await openCreateForm(user)

    // Submit with everything blank: ticker, brokerName, quantity,
    // unitPrice and tradedAt are all required.
    await user.click(within(form).getByRole('button', { name: /^guardar$/i }))

    const tickerInput = within(form).getByLabelText('Ticker')
    expect(tickerInput).toHaveAttribute('aria-invalid', 'true')
    expect(tickerInput).toHaveFocus()

    const alerts = within(form).getAllByRole('alert')
    expect(alerts.length).toBeGreaterThanOrEqual(5)
    expect(alerts.some((alert) => /ticker es obligatorio/i.test(alert.textContent ?? ''))).toBe(true)

    // The fees field is still optional and untouched: it keeps its
    // neutral hint, not an error.
    expect(within(form).getByLabelText('Comisiones')).toHaveAttribute('aria-invalid', 'false')
    expect(within(form).getByText('Opcional')).toBeInTheDocument()
  })

  it('invalidates a non-decimal amount with its own error and moves focus there when it is the first invalid field', async () => {
    mockList()
    const user = userEvent.setup()
    renderView()

    await screen.findByTestId('transaction-row-1')
    const form = await openCreateForm(user)

    await user.type(within(form).getByLabelText('Ticker'), 'QQQ')
    await user.type(within(form).getByLabelText('Bróker'), 'Hapi')
    await user.type(within(form).getByLabelText('Cantidad'), 'abc')
    await user.type(within(form).getByLabelText('Precio unitario'), '1')
    await user.type(within(form).getByLabelText('Fecha'), '2026-06-30')
    await user.click(within(form).getByRole('button', { name: /^guardar$/i }))

    const quantityInput = within(form).getByLabelText('Cantidad')
    expect(quantityInput).toHaveAttribute('aria-invalid', 'true')
    expect(quantityInput).toHaveFocus()
    expect(within(form).getByText(/cantidad debe ser un número decimal positivo/i)).toBeInTheDocument()
  })
})

describe('editing a transaction', () => {
  it('lets the user submit a future trade date and shows the server rejection rather than blocking it client-side', async () => {
    mockList()
    const user = userEvent.setup()
    renderView()

    const row = await screen.findByTestId('transaction-row-1')
    await user.click(within(row).getByRole('button', { name: /editar/i }))

    const form = screen.getByTestId('transaction-form')
    const dateField = within(form).getByLabelText('Fecha')

    let capturedBody: Record<string, unknown> | null = null
    mockUpdate((_id, body) => {
      capturedBody = body
      return HttpResponse.json(
        {
          type: 'about:blank',
          title: 'Bad Request',
          status: 400,
          detail: 'tradedAt no puede ser una fecha futura',
          instance: '/api/v1/transactions/1',
        },
        { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
      )
    })

    await user.clear(dateField)
    await user.type(dateField, '2099-01-01')
    await user.click(within(form).getByRole('button', { name: /^guardar$/i }))

    // The client never blocked the future date: the request actually left the browser
    // with it, and the server's own rejection is what renders.
    await waitFor(() => expect(capturedBody?.tradedAt).toBe('2099-01-01'))
    expect(await screen.findByText('tradedAt no puede ser una fecha futura')).toBeInTheDocument()
  })

  it('submits a valid edit, closes the form, and shows the updated row once the invalidated list refetches', async () => {
    const updatedTx = { ...txQqq, quantity: '0.5000000000' }
    let listCallCount = 0
    server.use(
      http.get(`${API_BASE}/transactions`, () => {
        listCallCount += 1
        // First load (initial render) returns the original row; the second load —
        // triggered only by the mutation's own invalidation, not a manual refetch —
        // returns the recalculated one. If the mutation stopped invalidating the
        // transactions query, listCallCount would stay at 1 and the row would never
        // show the updated quantity.
        return HttpResponse.json(listCallCount === 1 ? [txQqq, txVoo] : [updatedTx, txVoo])
      }),
    )

    const user = userEvent.setup()
    renderView()

    const row = await screen.findByTestId('transaction-row-1')
    await user.click(within(row).getByRole('button', { name: /editar/i }))

    const form = screen.getByTestId('transaction-form')
    const quantityField = within(form).getByLabelText('Cantidad')

    const captured: { body: Record<string, unknown> | null } = { body: null }
    mockUpdate((_id, body) => {
      captured.body = body
      return HttpResponse.json(updatedTx, { status: 200 })
    })

    await user.clear(quantityField)
    await user.type(quantityField, '0.5000000000')
    await user.click(within(form).getByRole('button', { name: /^guardar$/i }))

    await waitFor(() => expect(captured.body?.quantity).toBe('0.5000000000'))
    // The fields the user did not touch still travel with the edit, unchanged —
    // an edit is a full replacement of the trade, not a partial patch.
    expect(captured.body?.ticker).toBe('QQQ')
    expect(captured.body?.brokerName).toBe('Hapi')
    expect(captured.body?.type).toBe('BUY')
    expect(captured.body?.tradedAt).toBe('2026-06-30')

    // The form closes on success — unlike the 400 case above, where it stays open.
    await waitFor(() => expect(screen.queryByTestId('transaction-form')).not.toBeInTheDocument())

    // The recalculated row (this is where an average-cost recalculation would
    // surface) renders only because the mutation invalidated the list and it
    // refetched — not because of any client-side merge of the response.
    await waitFor(() => {
      const updatedRow = screen.getByTestId('transaction-row-1')
      expect(within(updatedRow).getByText('0.5')).toBeInTheDocument()
    })
  })
})

describe('deleting a transaction', () => {
  it('asks for an in-page confirmation naming the transaction before calling the API', async () => {
    mockList()
    mockDelete()
    const user = userEvent.setup()
    renderView()

    const row = await screen.findByTestId('transaction-row-1')
    await user.click(within(row).getByRole('button', { name: /eliminar/i }))

    // No DELETE request yet: the confirmation step is not an immediate call.
    expect(deleteCallCount).toBe(0)

    const confirmation = screen.getByTestId('delete-confirm-1')
    expect(confirmation).toHaveTextContent('BUY')
    expect(confirmation).toHaveTextContent('0.486372')
    expect(confirmation).toHaveTextContent('QQQ')
    expect(confirmation).toHaveTextContent('2026-06-30')

    await user.click(within(confirmation).getByRole('button', { name: /confirmar/i }))

    await waitFor(() => expect(deleteCallCount).toBe(1))
    expect(lastDeletedId).toBe('1')
  })

  it('cancelling the confirmation never calls the API', async () => {
    mockList()
    mockDelete()
    const user = userEvent.setup()
    renderView()

    const row = await screen.findByTestId('transaction-row-1')
    await user.click(within(row).getByRole('button', { name: /eliminar/i }))
    const confirmation = screen.getByTestId('delete-confirm-1')
    await user.click(within(confirmation).getByRole('button', { name: /cancelar/i }))

    expect(screen.queryByTestId('delete-confirm-1')).not.toBeInTheDocument()
    expect(deleteCallCount).toBe(0)
  })
})
