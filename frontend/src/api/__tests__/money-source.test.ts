import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

// This test reads money.ts's own source rather than exercising its
// behaviour, so the rule survives a refactor by someone who has not read
// the brief: no float coercion may ever touch an amount, no matter how
// formatMoney/formatPercent/formatScore end up implemented internally.
//
// Known limitation: this only reads money.ts itself. If coercion moved
// into a helper this module imports, this scan would not see it — it is
// a guard against a refactor within this file, not a module-graph walker.
describe('money.ts source', () => {
  const sourcePath = resolve(dirname(fileURLToPath(import.meta.url)), '../money.ts')
  const rawSource = readFileSync(sourcePath, 'utf-8')

  // Strip comments first so documentation that NAMES the forbidden
  // patterns (as this very module's own doc comments do, to explain the
  // rule) cannot produce a false positive. Only executable code is
  // checked for the real violation.
  const codeOnly = rawSource.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '')

  // Collapse all whitespace so spacing (e.g. "Number (x)" or a newline
  // between "+" and "amount") cannot evade the check.
  const normalized = codeOnly.replace(/\s+/g, '').toLowerCase()
  const source = codeOnly

  it('never calls Number(...) on anything', () => {
    expect(normalized).not.toContain('number(')
  })

  it('never calls parseFloat (bare or via Number.parseFloat)', () => {
    expect(normalized).not.toContain('parsefloat')
  })

  it('never calls parseInt', () => {
    expect(normalized).not.toContain('parseint')
  })

  it('never multiplies or divides an amount/value/digit/fraction-like identifier', () => {
    // Catches "amount * ...", "... * amount", "amount / 10", etc. — both
    // multiplication and division coerce a string operand through a
    // float just as surely as Number() or parseFloat would.
    expect(normalized).not.toMatch(/[*/]\(?[a-z0-9_.]*(amount|value|digit|fraction)\b/)
    expect(normalized).not.toMatch(/(amount|value|digit|fraction)[a-z0-9_.]*\)?[*/]/)
  })

  it('never applies a unary + to an amount-like identifier', () => {
    // Catches "+amount", "+ amount", "+money.amount", "+(money.amount)",
    // "+this.amount", case-insensitively, with any amount of whitespace
    // or an optional wrapping parenthesis already removed by normalizing.
    expect(normalized).not.toMatch(/\+\(?[a-z0-9_.]*amount\b/)
  })

  it('never applies a unary + to a fraction/integer/digit-like identifier either', () => {
    // Guards against renaming "amount" to something else while still
    // coercing it, e.g. "+value" or "+digits" where that variable holds
    // the raw decimal string.
    expect(normalized).not.toMatch(/\+\(?[a-z0-9_.]*(value|digit|fraction)\b/)
  })

  it('contains no bare unary plus operator at all', () => {
    // The module only ever needs string concatenation via template
    // literals, so a defence-in-depth check: no standalone "+" preceded
    // by an operator/parenthesis/assignment context survives, which is
    // where a unary plus would appear. Binary "+" between two template
    // placeholders inside `${}` never appears in this file either.
    expect(source).not.toMatch(/[^+\-*/%<>=&|,(\s]\s*\n?\s*\+\s*[a-zA-Z_$]/)
  })
})
