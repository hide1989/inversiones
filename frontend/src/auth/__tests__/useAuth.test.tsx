import { renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useAuth } from '../useAuth'

describe('useAuth used outside AuthProvider', () => {
  it('throws a clear error rather than returning undefined', () => {
    expect(() => renderHook(() => useAuth())).toThrow(/useAuth must be used within an AuthProvider/)
  })
})
