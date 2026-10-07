import { useDiversification } from '../api/queries/diversification'
import { formatMoney, formatPercent } from '../api/money'
import { compareDecimalStrings } from '../api/decimalCompare'
import { Surface, Tag } from '../design/primitives'
import { EmptyState, ErrorState, LoadingState } from '../components/state'
import type { DiversificationReport, DiversificationViolation } from '../api/schemas/diversification'

/**
 * A ceiling to render as a reference marker on a weight's track.
 * `value` is a literal percentage string (never computed) interpolated
 * straight into CSS — see the module doc below for why.
 */
interface Ceiling {
  label: string
  value: string
}

// The three ceilings this plan's contract fixes: 20% per stock, 25% per
// fund, 30% per sector. These are the plan's own constants, not derived
// from the response, so string-literal percentages here are not the
// "arithmetic on a weight" non-negotiable #1 forbids.
const SECTOR_CEILING: Ceiling = { label: 'Límite sector', value: '30' }

// `weightByTicker` carries no instrument-kind field (confirmed against
// the live backend response), so a ticker cannot be known to be a stock
// (20% ceiling) or a fund (25% ceiling) from this map alone. Rather than
// guess from a hardcoded ETF list, every ticker row shows both ceilings
// as explicit, separately labelled references — the honest version of
// "the ceiling must be visible" when which ceiling applies is genuinely
// unknown. See task-10-report.md for the full reasoning.
const TICKER_CEILINGS: Ceiling[] = [
  { label: 'Límite acción', value: '20' },
  { label: 'Límite fondo', value: '25' },
]

function findViolation(violations: ReadonlyArray<DiversificationViolation>, subject: string) {
  return violations.find((violation) => violation.subject === subject)
}

// `DiversificationViolationDto.from()` serializes `violation.limit().name()`
// straight onto the wire, so the real values are the Java enum's constant
// names — SINGLE_EQUITY, SINGLE_ETF, SECTOR — never translated server-side.
// A Spanish-speaking user should not see the literal constant, so each is
// mapped to a short Spanish label here. An unrecognised value (a limit
// kind this map has not been told about yet) renders verbatim rather than
// throwing or going blank — the honest fallback when the wire sends
// something this view does not know how to label.
const LIMIT_LABELS: Record<string, string> = {
  SINGLE_EQUITY: 'acción individual',
  SINGLE_ETF: 'fondo individual',
  SECTOR: 'sector',
}

function limitLabel(limit: string): string {
  return LIMIT_LABELS[limit] ?? limit
}

/**
 * A fixed 0-100% track. The filled bar's `width` and each ceiling
 * marker's `left` are literal string interpolations of percentage
 * strings the backend already computed (or this plan's own fixed
 * ceiling constants) straight into a CSS value — no division, no
 * `Number()`, nothing coerced. A 3% weight against a 20% ceiling
 * therefore renders as a short bar in a mostly-empty track: that is the
 * honest picture of a portfolio nowhere near its limit, not a bug to
 * design away by normalizing the track to the ceiling.
 */
function WeightTrack({ weight, ceilings, exceeded }: { weight: string; ceilings: Ceiling[]; exceeded: boolean }) {
  return (
    <div className="relative h-2 w-full overflow-visible rounded-sm bg-surface-3" aria-hidden="true">
      <div
        className={`absolute inset-y-0 left-0 rounded-sm ${exceeded ? 'bg-negative' : 'bg-accent'}`}
        style={{ width: `${weight}%` }}
      />
      {ceilings.map((ceiling) => (
        <div
          key={ceiling.label}
          className="absolute inset-y-[-2px] w-px bg-fg"
          style={{ left: `${ceiling.value}%` }}
        />
      ))}
    </div>
  )
}

function ViolationNote({ violation }: { violation: DiversificationViolation }) {
  return (
    <p className="font-sans text-xs text-negative">
      Excede el límite ({limitLabel(violation.limit)}): {formatPercent(violation.actualWeight)} de un máximo de{' '}
      {formatPercent(violation.allowedWeight)}.
    </p>
  )
}

function WeightRow({
  subject,
  weight,
  ceilings,
  violation,
}: {
  subject: string
  weight: string
  ceilings: Ceiling[]
  violation: DiversificationViolation | undefined
}) {
  return (
    <li data-testid={`weight-row-${subject}`} className="flex flex-col gap-1 py-2">
      <div className="flex items-center justify-between gap-2">
        <span className="font-medium text-fg">{subject}</span>
        <div className="flex items-center gap-2">
          {violation !== undefined && <Tag label="Excede el límite" tone="negative" />}
          <span className="font-mono tabular-nums text-fg">{formatPercent(weight)}</span>
        </div>
      </div>
      <WeightTrack weight={weight} ceilings={ceilings} exceeded={violation !== undefined} />
      <span className="font-sans text-xs text-fg-subtle">
        {ceilings.map((ceiling) => `${ceiling.label} ${ceiling.value}%`).join(' · ')}
      </span>
      {violation !== undefined && <ViolationNote violation={violation} />}
    </li>
  )
}

/** `compliant` and `violations` come straight from the backend — never a client-side threshold. */
function ComplianceBanner({ report }: { report: DiversificationReport }) {
  if (report.compliant) {
    return (
      <Surface level={1} className="flex items-center gap-2 p-4">
        <Tag label="Cumple todos los límites" tone="positive" />
        <span className="font-sans text-sm text-fg-muted">
          Valor total: {formatMoney(report.totalMarketValue)}
        </span>
      </Surface>
    )
  }

  return (
    <Surface level={1} className="flex flex-col gap-3 p-4">
      <div className="flex items-center gap-2">
        <Tag label="No cumple los límites de concentración" tone="negative" />
        <span className="font-sans text-sm text-fg-muted">
          Valor total: {formatMoney(report.totalMarketValue)}
        </span>
      </div>
      <ul className="flex flex-col gap-1">
        {report.violations.map((violation) => (
          <li key={`${violation.subject}-${violation.limit}`} className="font-sans text-sm text-negative">
            {violation.subject}: {formatPercent(violation.actualWeight)} de un máximo de{' '}
            {formatPercent(violation.allowedWeight)} ({limitLabel(violation.limit)})
          </li>
        ))}
      </ul>
    </Surface>
  )
}

/** Descending by weight, via the string comparator — never a parsed number. */
function sortByWeightDescending(entries: Array<[string, string]>): Array<[string, string]> {
  return [...entries].sort(([, weightA], [, weightB]) => compareDecimalStrings(weightB, weightA))
}

export function Diversificacion() {
  const { data, isPending, isError, error, refetch } = useDiversification()

  if (isPending) {
    return <LoadingState rows={6} />
  }

  if (isError) {
    const message = error instanceof Error ? error.message : undefined
    return <ErrorState message={message} onRetry={() => refetch()} />
  }

  const tickerEntries = Object.entries(data.weightByTicker)
  const sectorEntries = Object.entries(data.weightBySector)

  if (tickerEntries.length === 0 && sectorEntries.length === 0) {
    return (
      <div className="flex flex-col gap-6">
        <h1 className="font-mono text-xl font-semibold text-fg">Diversificación</h1>
        <EmptyState message="No hay posiciones para calcular la diversificación." />
      </div>
    )
  }

  const sortedTickers = sortByWeightDescending(tickerEntries)
  const sortedSectors = sortByWeightDescending(sectorEntries)

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-mono text-xl font-semibold text-fg">Diversificación</h1>
      <ComplianceBanner report={data} />
      <section aria-labelledby="ticker-weights-heading">
        <h2 id="ticker-weights-heading" className="mb-1 font-mono text-sm font-semibold uppercase tracking-wide text-fg-muted">
          Concentración por instrumento
        </h2>
        <p className="mb-2 font-sans text-xs text-fg-subtle">
          La respuesta no indica si un ticker es una acción o un fondo, así que cada fila muestra ambos límites
          posibles.
        </p>
        <ul>
          {sortedTickers.map(([ticker, weight]) => (
            <WeightRow
              key={ticker}
              subject={ticker}
              weight={weight}
              ceilings={TICKER_CEILINGS}
              violation={findViolation(data.violations, ticker)}
            />
          ))}
        </ul>
      </section>
      <section aria-labelledby="sector-weights-heading">
        <h2 id="sector-weights-heading" className="mb-1 font-mono text-sm font-semibold uppercase tracking-wide text-fg-muted">
          Concentración por sector
        </h2>
        <ul>
          {sortedSectors.map(([sector, weight]) => (
            <WeightRow
              key={sector}
              subject={sector}
              weight={weight}
              ceilings={[SECTOR_CEILING]}
              violation={findViolation(data.violations, sector)}
            />
          ))}
        </ul>
      </section>
    </div>
  )
}
