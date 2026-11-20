// @vitest-environment jsdom
import { createElement, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReviewSkillDetail } from '@/api/types'
import { useReviewSkillDetail } from './use-review-detail'

const { getSkillDetail } = vi.hoisted(() => ({ getSkillDetail: vi.fn() }))
vi.mock('@/api/client', () => ({ reviewApi: { getSkillDetail } }))

function detail(status: string): ReviewSkillDetail {
  return { activeVersion: '1.0.0', versions: [{ version: '1.0.0', status }] } as ReviewSkillDetail
}

function wrapper(client: QueryClient) {
  return ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children)
}

describe('review scan completion', () => {
  beforeEach(() => { vi.useFakeTimers(); getSkillDetail.mockReset() })
  afterEach(() => { vi.useRealTimers() })

  it('refreshes the active scanning version so approval can recover, then stops', async () => {
    getSkillDetail.mockResolvedValueOnce(detail('SCANNING')).mockResolvedValue(detail('PENDING_REVIEW'))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
    const { result, unmount } = renderHook(() => useReviewSkillDetail(3), { wrapper: wrapper(client) })
    try {
      await vi.waitFor(() => expect(result.current.data?.versions[0].status).toBe('SCANNING'))
      await act(() => vi.advanceTimersByTimeAsync(5000))
      await vi.waitFor(() => expect(result.current.data?.versions[0].status).toBe('PENDING_REVIEW'))
      await act(() => vi.advanceTimersByTimeAsync(15_000))
      expect(getSkillDetail).toHaveBeenCalledTimes(2)
    } finally { unmount(); client.clear() }
  })

  it.each(['PENDING_REVIEW', 'SCAN_FAILED', 'REJECTED', 'PUBLISHED'])('does not poll an active version in %s', async status => {
    const snapshot = detail(status)
    snapshot.versions.push({ ...snapshot.versions[0], version: '0.9.0', status: 'SCANNING' })
    getSkillDetail.mockResolvedValue(snapshot)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
    const { unmount } = renderHook(() => useReviewSkillDetail(3), { wrapper: wrapper(client) })
    try {
      await vi.waitFor(() => expect(getSkillDetail).toHaveBeenCalledTimes(1))
      await act(() => vi.advanceTimersByTimeAsync(15_000))
      expect(getSkillDetail).toHaveBeenCalledTimes(1)
    } finally { unmount(); client.clear() }
  })
})
