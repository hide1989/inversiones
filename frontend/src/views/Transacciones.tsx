import { useId, useState, type FormEvent } from 'react'
import {
  useCreateTransaction,
  useDeleteTransaction,
  useTransactions,
  useUpdateTransaction,
  type TransactionFilters,
} from '../api/queries/transactions'
import { formatMoney, formatQuantity } from '../api/money'
import { ApiError } from '../api/problem'
import { Button, Field, Surface, Tag } from '../design/primitives'
import { DataTable, type DataTableColumn } from '../components/table'
import { EmptyState, ErrorState, LoadingState } from '../components/state'
import type { TransactionRequest, TransactionResponse } from '../api/schemas/transactions'
import type { Currency } from '../api/schemas/common'

// Mirrors exactly the regex TransactionRequest applies server-side
// (`^\d+(\.\d+)?$`): non-negative, no leading `+`/`-`, at least one
// integer digit. Business rules — a future trade date, a sale exceeding
// the holding, an unknown ticker, an instrument change on edit — are
// deliberately NOT checked here; those are the backend's to reject, and
// its ProblemDetail renders through `MutationProblem` below instead of a
// client-side block that could drift from the server's own rule.
const POSITIVE_DECIMAL_PATTERN = /^\d+(\.\d+)?$/

type TransactionTypeValue = 'BUY' | 'SELL'

interface FormState {
  ticker: string
  brokerName: string
  type: TransactionTypeValue
  quantity: string
  unitPrice: string
  currency: Currency
  fees: string
  tradedAt: string
}

const EMPTY_FORM: FormState = {
  ticker: '',
  brokerName: '',
  type: 'BUY',
  quantity: '',
  unitPrice: '',
  currency: 'USD',
  fees: '',
  tradedAt: '',
}

function formStateFromTransaction(transaction: TransactionResponse): FormState {
  return {
    ticker: transaction.ticker,
    brokerName: transaction.brokerName,
    type: transaction.type === 'SELL' ? 'SELL' : 'BUY',
    quantity: transaction.quantity,
    unitPrice: transaction.unitPrice.amount,
    currency: transaction.unitPrice.currency,
    fees: transaction.fees.amount,
    tradedAt: transaction.tradedAt,
  }
}

interface FormErrors {
  ticker?: string
  brokerName?: string
  quantity?: string
  unitPrice?: string
  fees?: string
  tradedAt?: string
}

function validate(form: FormState): FormErrors {
  const errors: FormErrors = {}
  if (form.ticker.trim() === '') errors.ticker = 'El ticker es obligatorio.'
  if (form.brokerName.trim() === '') errors.brokerName = 'El bróker es obligatorio.'
  if (form.quantity.trim() === '') {
    errors.quantity = 'La cantidad es obligatoria.'
  } else if (!POSITIVE_DECIMAL_PATTERN.test(form.quantity.trim())) {
    errors.quantity = 'La cantidad debe ser un número decimal positivo.'
  }
  if (form.unitPrice.trim() === '') {
    errors.unitPrice = 'El precio unitario es obligatorio.'
  } else if (!POSITIVE_DECIMAL_PATTERN.test(form.unitPrice.trim())) {
    errors.unitPrice = 'El precio unitario debe ser un número decimal positivo.'
  }
  if (form.fees.trim() !== '' && !POSITIVE_DECIMAL_PATTERN.test(form.fees.trim())) {
    errors.fees = 'Las comisiones deben ser un número decimal positivo.'
  }
  if (form.tradedAt.trim() === '') errors.tradedAt = 'La fecha es obligatoria.'
  return errors
}

/**
 * Builds the exact request body from form text. Only `.trim()` ever
 * touches `quantity`, `unitPrice` or `fees` here — never `Number()`,
 * `parseFloat`, nor any arithmetic operator — so a pasted
 * `"0.4863720000"` reaches `JSON.stringify` unchanged. `fees` is omitted
 * (not sent as `""`) when blank, since the backend's `@Pattern` rejects
 * an empty string but accepts a missing field.
 */
function toRequestBody(form: FormState): TransactionRequest {
  const fees = form.fees.trim()
  return {
    ticker: form.ticker.trim(),
    brokerName: form.brokerName.trim(),
    type: form.type,
    quantity: form.quantity.trim(),
    unitPrice: form.unitPrice.trim(),
    currency: form.currency,
    fees: fees === '' ? undefined : fees,
    tradedAt: form.tradedAt,
  }
}

function problemDetailOf(error: unknown): string | null {
  return error instanceof ApiError ? error.problem.detail : null
}

function SelectField({
  label,
  value,
  onChange,
  disabled,
  options,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  disabled: boolean
  options: ReadonlyArray<{ value: string; label: string }>
}) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="font-mono text-xs font-medium uppercase tracking-wide text-fg-muted">
        {label}
      </label>
      <select
        id={id}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
        className="rounded-sm border border-border bg-surface-2 px-2 py-1.5 font-mono text-sm text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:cursor-not-allowed disabled:opacity-40"
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  )
}

interface TransactionFormProps {
  title: string
  initial: FormState
  submitting: boolean
  problem: string | null
  onSubmit: (body: TransactionRequest) => void
  onCancel: () => void
}

/** The create/edit form shared by both modes — the caller decides whether `initial` came from `EMPTY_FORM` or an existing row. */
function TransactionForm({ title, initial, submitting, problem, onSubmit, onCancel }: TransactionFormProps) {
  const [form, setForm] = useState<FormState>(initial)
  const [errors, setErrors] = useState<FormErrors>({})

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const validationErrors = validate(form)
    setErrors(validationErrors)
    if (Object.keys(validationErrors).length > 0) return
    onSubmit(toRequestBody(form))
  }

  return (
    <Surface level={1} data-testid="transaction-form" className="flex flex-col gap-4 p-4">
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
        <h2 className="font-mono text-sm font-semibold uppercase tracking-wide text-fg-muted">{title}</h2>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          <Field
            label="Ticker"
            value={form.ticker}
            onChange={(event) => setForm({ ...form, ticker: event.target.value })}
            disabled={submitting}
            hint={errors.ticker}
          />
          <Field
            label="Bróker"
            value={form.brokerName}
            onChange={(event) => setForm({ ...form, brokerName: event.target.value })}
            disabled={submitting}
            hint={errors.brokerName}
          />
          <SelectField
            label="Tipo"
            value={form.type}
            onChange={(value) => setForm({ ...form, type: value === 'SELL' ? 'SELL' : 'BUY' })}
            disabled={submitting}
            options={[
              { value: 'BUY', label: 'Compra' },
              { value: 'SELL', label: 'Venta' },
            ]}
          />
          <SelectField
            label="Divisa"
            value={form.currency}
            onChange={(value) => setForm({ ...form, currency: value === 'COP' ? 'COP' : 'USD' })}
            disabled={submitting}
            options={[
              { value: 'USD', label: 'USD' },
              { value: 'COP', label: 'COP' },
            ]}
          />
          {/* Amount/quantity inputs: type="text", never type="number" — a number
              input coerces through an IEEE double via `valueAsNumber` and the
              browser's own normalization, corrupting scale 10 before the
              request ever leaves the browser. */}
          <Field
            label="Cantidad"
            type="text"
            inputMode="decimal"
            value={form.quantity}
            onChange={(event) => setForm({ ...form, quantity: event.target.value })}
            disabled={submitting}
            hint={errors.quantity}
          />
          <Field
            label="Precio unitario"
            type="text"
            inputMode="decimal"
            value={form.unitPrice}
            onChange={(event) => setForm({ ...form, unitPrice: event.target.value })}
            disabled={submitting}
            hint={errors.unitPrice}
          />
          <Field
            label="Comisiones"
            type="text"
            inputMode="decimal"
            value={form.fees}
            onChange={(event) => setForm({ ...form, fees: event.target.value })}
            disabled={submitting}
            hint={errors.fees ?? 'Opcional'}
          />
          <Field
            label="Fecha"
            type="date"
            value={form.tradedAt}
            onChange={(event) => setForm({ ...form, tradedAt: event.target.value })}
            disabled={submitting}
            hint={errors.tradedAt}
          />
        </div>

        {problem !== null && (
          <p role="alert" className="font-sans text-sm text-negative">
            {problem}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onCancel} disabled={submitting}>
            Cancelar
          </Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Guardando…' : 'Guardar'}
          </Button>
        </div>
      </form>
    </Surface>
  )
}

interface ActionsCellProps {
  transaction: TransactionResponse
  isConfirmingDelete: boolean
  isDeleting: boolean
  onEdit: () => void
  onRequestDelete: () => void
  onCancelDelete: () => void
  onConfirmDelete: () => void
}

/** Naming the transaction (type, quantity, ticker, date) so a confirm click cannot delete the wrong row by muscle memory — an in-page confirmation, never `window.confirm`, which would block the automation this project is tested with. */
function ActionsCell({
  transaction,
  isConfirmingDelete,
  isDeleting,
  onEdit,
  onRequestDelete,
  onCancelDelete,
  onConfirmDelete,
}: ActionsCellProps) {
  if (isConfirmingDelete) {
    return (
      <div className="flex flex-col items-end gap-1" data-testid={`delete-confirm-${transaction.id}`}>
        <span className="font-sans text-xs text-fg-muted">
          ¿Eliminar {transaction.type} {formatQuantity(transaction.quantity)} {transaction.ticker} del{' '}
          {transaction.tradedAt}?
        </span>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={onCancelDelete} disabled={isDeleting}>
            Cancelar
          </Button>
          <Button onClick={onConfirmDelete} disabled={isDeleting}>
            {isDeleting ? 'Eliminando…' : 'Confirmar'}
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex justify-end gap-2">
      <Button variant="ghost" onClick={onEdit}>
        Editar
      </Button>
      <Button variant="ghost" onClick={onRequestDelete}>
        Eliminar
      </Button>
    </div>
  )
}

function buildColumns(options: {
  deleteTargetId: number | null
  isDeleting: boolean
  onEdit: (transaction: TransactionResponse) => void
  onRequestDelete: (id: number) => void
  onCancelDelete: () => void
  onConfirmDelete: (id: number) => void
}): DataTableColumn<TransactionResponse>[] {
  return [
    { key: 'ticker', header: 'Ticker', align: 'left', render: (row) => row.ticker },
    { key: 'brokerName', header: 'Bróker', align: 'left', render: (row) => <Tag label={row.brokerName} /> },
    { key: 'type', header: 'Tipo', render: (row) => row.type },
    { key: 'quantity', header: 'Cantidad', render: (row) => formatQuantity(row.quantity) },
    { key: 'unitPrice', header: 'Precio unitario', render: (row) => formatMoney(row.unitPrice) },
    { key: 'fees', header: 'Comisiones', render: (row) => formatMoney(row.fees) },
    { key: 'tradedAt', header: 'Fecha', render: (row) => row.tradedAt },
    { key: 'grossAmount', header: 'Monto bruto', render: (row) => formatMoney(row.grossAmount) },
    { key: 'netAmount', header: 'Monto neto', render: (row) => formatMoney(row.netAmount) },
    {
      key: 'actions',
      header: 'Acciones',
      width: '14rem',
      render: (row) => (
        <ActionsCell
          transaction={row}
          isConfirmingDelete={options.deleteTargetId === row.id}
          isDeleting={options.isDeleting}
          onEdit={() => options.onEdit(row)}
          onRequestDelete={() => options.onRequestDelete(row.id)}
          onCancelDelete={options.onCancelDelete}
          onConfirmDelete={() => options.onConfirmDelete(row.id)}
        />
      ),
    },
  ]
}

type ModalState = { mode: 'closed' } | { mode: 'create' } | { mode: 'edit'; transaction: TransactionResponse }

/** Filters: text for ticker/broker, dates for the range — each becomes a `TransactionFilters` field that drives `GET /transactions`'s own query parameters. No client-side filtering of an unfiltered fetch. */
function FiltersPanel({
  filters,
  onChange,
}: {
  filters: TransactionFilters
  onChange: (next: TransactionFilters) => void
}) {
  return (
    <Surface level={1} className="grid grid-cols-2 gap-4 p-4 sm:grid-cols-4">
      <Field
        label="Ticker"
        value={filters.ticker ?? ''}
        onChange={(event) => onChange({ ...filters, ticker: event.target.value })}
      />
      <Field
        label="Bróker"
        value={filters.brokerName ?? ''}
        onChange={(event) => onChange({ ...filters, brokerName: event.target.value })}
      />
      <Field
        label="Desde"
        type="date"
        value={filters.from ?? ''}
        onChange={(event) => onChange({ ...filters, from: event.target.value })}
      />
      <Field
        label="Hasta"
        type="date"
        value={filters.to ?? ''}
        onChange={(event) => onChange({ ...filters, to: event.target.value })}
      />
    </Surface>
  )
}

export function Transacciones() {
  const [filters, setFilters] = useState<TransactionFilters>({})
  const [modal, setModal] = useState<ModalState>({ mode: 'closed' })
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null)

  const { data, isPending, isError, error, refetch } = useTransactions(filters)
  const createMutation = useCreateTransaction()
  const updateMutation = useUpdateTransaction()
  const deleteMutation = useDeleteTransaction()

  function closeForm() {
    setModal({ mode: 'closed' })
    createMutation.reset()
    updateMutation.reset()
  }

  async function handleCreate(body: TransactionRequest) {
    try {
      await createMutation.mutateAsync(body)
      closeForm()
    } catch {
      // Swallowed: createMutation.error below drives the rendered ProblemDetail.
    }
  }

  async function handleUpdate(id: number, body: TransactionRequest) {
    try {
      await updateMutation.mutateAsync({ id, body })
      closeForm()
    } catch {
      // Swallowed: updateMutation.error below drives the rendered ProblemDetail.
      // A future trade date or an instrument change lands here, rendered
      // verbatim from the backend rather than blocked before submission.
    }
  }

  function cancelDelete() {
    setDeleteTargetId(null)
    deleteMutation.reset()
  }

  async function confirmDelete(id: number) {
    try {
      await deleteMutation.mutateAsync(id)
      setDeleteTargetId(null)
    } catch {
      // Swallowed: deleteMutation.error renders below; the confirmation stays open.
    }
  }

  const columns = buildColumns({
    deleteTargetId,
    isDeleting: deleteMutation.isPending,
    onEdit: (transaction) => setModal({ mode: 'edit', transaction }),
    onRequestDelete: (id) => setDeleteTargetId(id),
    onCancelDelete: cancelDelete,
    onConfirmDelete: confirmDelete,
  })

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="font-mono text-xl font-semibold text-fg">Transacciones</h1>
        <Button onClick={() => setModal({ mode: 'create' })}>Nueva transacción</Button>
      </div>

      <FiltersPanel filters={filters} onChange={setFilters} />

      {modal.mode === 'create' && (
        <TransactionForm
          title="Nueva transacción"
          initial={EMPTY_FORM}
          submitting={createMutation.isPending}
          problem={problemDetailOf(createMutation.error)}
          onSubmit={handleCreate}
          onCancel={closeForm}
        />
      )}

      {modal.mode === 'edit' && (
        <TransactionForm
          title={`Editar transacción #${modal.transaction.id}`}
          initial={formStateFromTransaction(modal.transaction)}
          submitting={updateMutation.isPending}
          problem={problemDetailOf(updateMutation.error)}
          onSubmit={(body) => handleUpdate(modal.transaction.id, body)}
          onCancel={closeForm}
        />
      )}

      {deleteMutation.isError && (
        <p role="alert" className="font-sans text-sm text-negative">
          {problemDetailOf(deleteMutation.error)}
        </p>
      )}

      {isPending && <LoadingState rows={6} />}

      {isError && (
        <ErrorState message={error instanceof Error ? error.message : undefined} onRetry={() => refetch()} />
      )}

      {!isPending && !isError && data.length === 0 && (
        <EmptyState message="No hay transacciones todavía." />
      )}

      {!isPending && !isError && data.length > 0 && (
        <DataTable
          caption="Transacciones registradas"
          columns={columns}
          rows={data}
          getRowKey={(row) => String(row.id)}
          getRowTestId={(row) => `transaction-row-${row.id}`}
        />
      )}
    </div>
  )
}
