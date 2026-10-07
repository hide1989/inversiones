import type { ReactNode } from 'react'
import { Button, Delta, Field, Figure, ScoreBar, Surface, Tag } from '../design/primitives'

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="font-mono text-sm font-semibold uppercase tracking-wide text-fg-muted">{title}</h2>
      <div className="flex flex-wrap items-start gap-4">{children}</div>
    </section>
  )
}

function State({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="font-mono text-xs text-fg-subtle">{label}</span>
      {children}
    </div>
  )
}

/**
 * Renders every primitive in every state (default, hover, focus, disabled,
 * positive, negative, zero, long value, missing value) so the design can
 * be judged before any real view depends on it. UI copy is Spanish.
 */
export function Specimen() {
  return (
    <div className="mx-auto flex max-w-[1200px] flex-col gap-10 px-6 py-10">
      <header className="flex flex-col gap-1">
        <h1 className="font-mono text-xl font-semibold text-fg">Specimen — sistema de diseño</h1>
        <p className="font-sans text-sm text-fg-muted">
          Cada primitivo renderizado en cada estado, para juzgar el diseño antes de construir vistas sobre él.
        </p>
      </header>

      <Section title="Surface — elevación por luminosidad">
        <State label="Nivel 1 (base)">
          <Surface level={1} className="h-20 w-40 p-3 font-mono text-xs text-fg-muted">
            bg-surface-1
          </Surface>
        </State>
        <State label="Nivel 2">
          <Surface level={2} className="h-20 w-40 p-3 font-mono text-xs text-fg-muted">
            bg-surface-2
          </Surface>
        </State>
        <State label="Nivel 3 (más alto)">
          <Surface level={3} className="h-20 w-40 p-3 font-mono text-xs text-fg-muted">
            bg-surface-3
          </Surface>
        </State>
      </Section>

      <Section title="Figure — valor numérico, tabular, alineado a la derecha">
        <State label="Valor por defecto">
          <Figure value="2,791.42" className="w-32" />
        </State>
        <State label="Valor largo">
          <Figure value="300.0000000000" className="w-48" />
        </State>
        <State label="Valor faltante">
          <Figure value="—" className="w-32" />
        </State>
        <State label="Cero">
          <Figure value="0.00" className="w-32" />
        </State>
      </Section>

      <Section title="Delta — signo, color y flecha">
        <State label="Positivo">
          <Delta value="328.42" sign="positive" className="w-32" />
        </State>
        <State label="Negativo">
          <Delta value="12.40" sign="negative" className="w-32" />
        </State>
        <State label="Cero">
          <Delta value="0.00" sign="zero" className="w-32" />
        </State>
        <State label="Valor largo, positivo">
          <Delta value="128,430.0000" sign="positive" className="w-48" />
        </State>
      </Section>

      <Section title="ScoreBar — puntaje 1-10">
        <State label="Alto (8.2)">
          <ScoreBar score="8.2" />
        </State>
        <State label="Medio (4.1)">
          <ScoreBar score="4.1" />
        </State>
        <State label="Bajo (1.0)">
          <ScoreBar score="1.0" />
        </State>
        <State label="Máximo (10.0)">
          <ScoreBar score="10.0" />
        </State>
        <State label="Faltante">
          <ScoreBar score="—" />
        </State>
      </Section>

      <Section title="Tag — nivel de señal o broker">
        <State label="Neutro">
          <Tag label="Hapi" tone="neutral" />
        </State>
        <State label="Acento">
          <Tag label="Enfocado" tone="accent" />
        </State>
        <State label="Positivo">
          <Tag label="Alta convicción" tone="positive" />
        </State>
        <State label="Negativo">
          <Tag label="Riesgo" tone="negative" />
        </State>
      </Section>

      <Section title="Button — único acento accionable">
        <State label="Por defecto (primary)">
          <Button variant="primary">Guardar</Button>
        </State>
        <State label="Foco (tab hasta aquí)">
          <Button variant="primary">Enfocar con tab</Button>
        </State>
        <State label="Deshabilitado">
          <Button variant="primary" disabled>
            Deshabilitado
          </Button>
        </State>
        <State label="Ghost">
          <Button variant="ghost">Cancelar</Button>
        </State>
        <State label="Ghost deshabilitado">
          <Button variant="ghost" disabled>
            Deshabilitado
          </Button>
        </State>
      </Section>

      <Section title="Field — input con etiqueta real">
        <State label="Por defecto">
          <Field label="Ticker" placeholder="QQQ" />
        </State>
        <State label="Con ayuda">
          <Field label="Monto" placeholder="0.00" hint="En USD, sin separador de miles" />
        </State>
        <State label="Deshabilitado">
          <Field label="Broker" defaultValue="Hapi" disabled />
        </State>
      </Section>
    </div>
  )
}
