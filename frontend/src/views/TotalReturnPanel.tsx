import { useTotalReturn } from '../api/queries/returns'
import { formatMoney } from '../api/money'
import { signOf } from '../api/decimalSign'
import { Delta, Figure, Surface } from '../design/primitives'
import { EmptyState, ErrorState, LoadingState } from '../components/state'
import type { ReturnSegment, TotalReturnResponse } from '../api/schemas/returns'

// Imported only from Posiciones.tsx, never from a shared module — this
// file is this task's whole reason to exist, so nothing in `design/` or
// `components/` depends on it, and nothing outside this lazy route pays
// for it. (No Recharts here: see the note above TwoComponentDisplay.)

const SEGMENT_LABEL: Record<ReturnSegment['segment'], string> = {
  USD_ASSETS: 'Activos USD',
  TRII_ADR: 'ADR en Trii',
  BVC_COLOMBIA: 'BVC Colombia',
}

function SegmentRow({ segment }: { segment: ReturnSegment }) {
  return (
    <tr data-testid={`segment-row-${segment.segment}`} className="border-b border-border/60 last:border-b-0">
      <th scope="row" className="py-2 text-left font-sans text-sm font-medium text-fg">
        {SEGMENT_LABEL[segment.segment]}
      </th>
      <td className="py-2">
        <Figure value={formatMoney(segment.invested)} />
      </td>
      <td className="py-2">
        <Figure value={formatMoney(segment.marketValue)} />
      </td>
      <td className="py-2">
        <Delta
          value={formatMoney({ amount: stripSign(segment.assetGain.amount), currency: segment.assetGain.currency }, { withCurrency: false })}
          sign={signOf(segment.assetGain.amount)}
          currency={segment.assetGain.currency}
        />
      </td>
    </tr>
  )
}

/** String slicing only — no numeric coercion — matching `magnitudeOf` in decimalSign.ts. */
function stripSign(amount: string): string {
  return amount.startsWith('-') ? amount.slice(1) : amount
}

function SegmentsTable({ segments }: { segments: ReadonlyArray<ReturnSegment> }) {
  return (
    <table className="w-full border-collapse font-mono text-sm tabular-nums">
      <caption className="mb-2 text-left font-sans text-sm font-semibold text-fg-muted">
        Segmentos del portafolio
      </caption>
      <thead>
        <tr className="border-b border-border text-xs uppercase tracking-wide text-fg-muted">
          <th scope="col" className="py-1 text-left">
            Segmento
          </th>
          <th scope="col" className="py-1 text-right">
            Invertido
          </th>
          <th scope="col" className="py-1 text-right">
            Valor mercado
          </th>
          <th scope="col" className="py-1 text-right">
            Ganancia por activos
          </th>
        </tr>
      </thead>
      <tbody>
        {segments.map((segment) => (
          <SegmentRow key={segment.segment} segment={segment} />
        ))}
      </tbody>
    </table>
  )
}

function RateRow({ label, rate }: { label: string; rate: { rate: string; rateDate: string } }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">{label}</span>
      <span className="font-mono text-sm tabular-nums text-fg">{rate.rate} COP/USD</span>
      <span className="font-mono text-xs tabular-nums text-fg-subtle">{rate.rateDate}</span>
    </div>
  )
}

/**
 * The asset gain and the exchange effect, side by side, each its own
 * `Figure` with the currency spelled out. Neither is summed here — the
 * total below is the API's own `totalReturnInCop`, rendered separately
 * and deliberately smaller, so the two forces that produced it stay the
 * primary thing a reader sees.
 *
 * No Recharts, and no hand-rolled bar either: a proportional bar would
 * need a width computed from the two amounts, which is exactly the kind
 * of browser-side numeric reasoning about money the plan's non-negotiable
 * #2 exists to keep out of this panel — and two clearly labelled figures
 * already deliver the "one nearly cancels the other" insight as text,
 * which is also what the accessibility rule on bars (readable as text,
 * not just width) already demands. Two divs, no dependency, no
 * arithmetic on the amounts.
 */
function TwoComponentDisplay({ data }: { data: TotalReturnResponse }) {
  if (!data.currencyEffectAvailable || data.exchangeEffect === null || data.assetGainInCop === null) {
    return (
      <div data-testid="currency-effect-region" role="note" className="rounded-md border border-accent/40 bg-accent/10 p-4">
        <p className="font-sans text-sm text-fg">
          No fue posible obtener la tasa de cambio actual, así que el efecto cambiario y el retorno total en pesos no
          se pueden calcular en este momento. La ganancia por activos de cada segmento, y la tasa TRM base, siguen
          disponibles abajo.
        </p>
      </div>
    )
  }

  const assetGain = data.assetGainInCop
  const exchangeEffect = data.exchangeEffect

  return (
    <div data-testid="currency-effect-region" className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Surface level={2} className="flex flex-col gap-2 p-4">
          <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">Ganancia por activos (COP)</span>
          <div data-testid="asset-gain-figure" className="text-2xl">
            <Delta
              value={formatMoney({ amount: stripSign(assetGain.amount), currency: assetGain.currency }, { withCurrency: false })}
              sign={signOf(assetGain.amount)}
            />
          </div>
        </Surface>
        <Surface level={2} className="flex flex-col gap-2 p-4">
          <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">Efecto cambiario (COP)</span>
          <div data-testid="exchange-effect-figure" className="text-2xl">
            <Delta
              value={formatMoney(
                { amount: stripSign(exchangeEffect.amount), currency: exchangeEffect.currency },
                { withCurrency: false },
              )}
              sign={signOf(exchangeEffect.amount)}
            />
          </div>
        </Surface>
      </div>
      {data.totalReturnInCop !== null && (
        <div className="flex items-baseline justify-between border-t border-border/60 pt-3">
          <span className="font-mono text-xs uppercase tracking-wide text-fg-muted">Retorno total (COP)</span>
          <div data-testid="total-return-figure" className="text-base">
            <Delta
              value={formatMoney(
                { amount: stripSign(data.totalReturnInCop.amount), currency: data.totalReturnInCop.currency },
                { withCurrency: false },
              )}
              sign={signOf(data.totalReturnInCop.amount)}
            />
          </div>
        </div>
      )}
    </div>
  )
}

export function TotalReturnPanel() {
  const { data, isPending, isError, error, refetch } = useTotalReturn()

  if (isPending) {
    return <LoadingState rows={3} />
  }

  if (isError) {
    const message = error instanceof Error ? error.message : undefined
    return <ErrorState message={message} onRetry={() => refetch()} />
  }

  if (data.segments.length === 0) {
    return <EmptyState message="No hay datos de retorno disponibles." />
  }

  return (
    <Surface level={1} data-testid="total-return-panel" className="flex w-full flex-col gap-6 p-4">
      <div>
        <h2 className="font-mono text-sm font-semibold uppercase tracking-wide text-fg-muted">
          Retorno en pesos
        </h2>
        <p className="font-sans text-xs text-fg-subtle">
          El portafolio se mide en dólares arriba; esta sección responde la pregunta en pesos — cuánto vino de los
          activos y cuánto del tipo de cambio.
        </p>
      </div>
      <TwoComponentDisplay data={data} />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <RateRow label="TRM base" rate={data.baseRate} />
        {data.currentRate !== null && <RateRow label="TRM actual" rate={data.currentRate} />}
      </div>
      <SegmentsTable segments={data.segments} />
    </Surface>
  )
}
