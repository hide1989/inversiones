import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const tokensPath = resolve(dirname(fileURLToPath(import.meta.url)), '../tokens.css')
const tokensSource = readFileSync(tokensPath, 'utf-8')

function readToken(name: string): string {
  const match = new RegExp(`${name}:\\s*(#[0-9a-fA-F]{6})`).exec(tokensSource)
  if (match === null || match[1] === undefined) {
    throw new Error(`Token ${name} not found in tokens.css`)
  }
  return match[1]
}

/** WCAG relative luminance of a single sRGB channel. */
function linearize(channel: number): number {
  const c = channel / 255
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
}

/** WCAG relative luminance of a #rrggbb colour. */
function relativeLuminance(hex: string): number {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
}

/** WCAG contrast ratio between two #rrggbb colours. */
function contrastRatio(a: string, b: string): number {
  const lumA = relativeLuminance(a)
  const lumB = relativeLuminance(b)
  const lighter = Math.max(lumA, lumB)
  const darker = Math.min(lumA, lumB)
  return (lighter + 0.05) / (darker + 0.05)
}

const MIN_CONTRAST = 4.5

describe('token contrast', () => {
  const ground = readToken('--color-ground')
  const surface3 = readToken('--color-surface-3')
  const fg = readToken('--color-fg')
  const fgMuted = readToken('--color-fg-muted')

  it('primary text meets 4.5:1 on the ground', () => {
    expect(contrastRatio(fg, ground)).toBeGreaterThanOrEqual(MIN_CONTRAST)
  })

  it('primary text meets 4.5:1 on the most raised surface', () => {
    expect(contrastRatio(fg, surface3)).toBeGreaterThanOrEqual(MIN_CONTRAST)
  })

  it('secondary text meets 4.5:1 on the ground', () => {
    expect(contrastRatio(fgMuted, ground)).toBeGreaterThanOrEqual(MIN_CONTRAST)
  })

  it('secondary text meets 4.5:1 on the most raised surface', () => {
    expect(contrastRatio(fgMuted, surface3)).toBeGreaterThanOrEqual(MIN_CONTRAST)
  })
})
