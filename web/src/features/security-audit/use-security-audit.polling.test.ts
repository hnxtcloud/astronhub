// @vitest-environment jsdom
import { createElement, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useSecurityAudits } from './use-security-audit'
import type { SecurityAuditRecord } from './types'

const { fetchJson } = vi.hoisted(() => ({ fetchJson: vi.fn() }))
vi.mock('@/api/client', () => ({ fetchJson, ApiError: class extends Error {}, getCsrfHeaders: vi.fn() }))

describe('security audit completion', () => {
  afterEach(() => { vi.useRealTimers(); fetchJson.mockReset() })

  it('refreshes the pending audit and stops once the scan result arrives', async () => {
    vi.useFakeTimers()
    const pending = { id: 1, scannedAt: null, failureReason: null, verdict: 'SUSPICIOUS' } as SecurityAuditRecord
    fetchJson.mockResolvedValueOnce([pending]).mockResolvedValue([{ ...pending, scannedAt: '2026-10-21T00:00:00Z', verdict: 'SAFE' }])
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
    const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children)
    const { result, unmount } = renderHook(() => useSecurityAudits(1, 2), { wrapper })
    try {
      await vi.waitFor(() => expect(result.current.data?.[0].verdict).toBe('SUSPICIOUS'))
      await act(() => vi.advanceTimersByTimeAsync(5000))
      await vi.waitFor(() => expect(result.current.data?.[0].verdict).toBe('SAFE'))
      await act(() => vi.advanceTimersByTimeAsync(15_000))
      expect(fetchJson).toHaveBeenCalledTimes(2)
    } finally { unmount(); client.clear() }
  })
})
