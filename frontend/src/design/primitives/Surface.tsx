import type { HTMLAttributes } from 'react'

export type SurfaceLevel = 1 | 2 | 3

export interface SurfaceProps extends HTMLAttributes<HTMLDivElement> {
  /** Elevation by lightness, not by border: higher levels sit on a lighter surface colour. */
  level?: SurfaceLevel
}

const LEVEL_CLASS: Record<SurfaceLevel, string> = {
  1: 'bg-surface-1',
  2: 'bg-surface-2',
  3: 'bg-surface-3',
}

/** A raised panel. */
export function Surface({ level = 1, className = '', ...rest }: SurfaceProps) {
  return <div className={`rounded-md ${LEVEL_CLASS[level]} ${className}`.trim()} {...rest} />
}
