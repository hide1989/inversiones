import AxeBuilder from '@axe-core/playwright'
import { expect, type Page } from '@playwright/test'

/** Runs the full axe ruleset against the current page and asserts zero violations, printing them (ids + the first offending node) on failure instead of a bare count. */
export async function expectNoAccessibilityViolations(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page }).analyze()
  const summary = results.violations.map((violation) => ({
    id: violation.id,
    impact: violation.impact,
    nodes: violation.nodes.map((node) => node.target.join(' ')),
  }))
  expect(summary, JSON.stringify(summary, null, 2)).toEqual([])
}
