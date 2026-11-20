// @vitest-environment jsdom
import { createElement, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { usePluginProject, type PluginProject } from './plugin-api'

const { fetchJson } = vi.hoisted(() => ({ fetchJson: vi.fn() }))
vi.mock('@/api/client', () => ({ fetchJson, namespaceApi: { listMine: vi.fn() }, getCsrfHeaders: vi.fn() }))

function snapshot(status: string, publicationStatus?: string): PluginProject {
  return {
    id: 39,
    releases: [{ id: 41, version: '0.1.2', status, publication: publicationStatus ? { status: publicationStatus } : null }],
  } as PluginProject
}
function wrapper(client: QueryClient) {
  return ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client }, children)
}

describe('plugin project publication polling', () => {
  beforeEach(() => { vi.useFakeTimers(); fetchJson.mockReset() })
  afterEach(() => { vi.useRealTimers() })

  it('refreshes a successful task until the release itself becomes published, then stops', async () => {
    // A real E2E response exposed SUCCEEDED before the release transaction was visible.
    fetchJson.mockResolvedValueOnce(snapshot('PENDING_REVIEW', 'SUCCEEDED'))
      .mockResolvedValue(snapshot('PUBLISHED', 'SUCCEEDED'))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
    const { result, unmount } = renderHook(() => usePluginProject(39), { wrapper: wrapper(client) })
    try {
      await vi.waitFor(() => expect(result.current.data?.releases[0].status).toBe('PENDING_REVIEW'))
      await act(() => vi.advanceTimersByTimeAsync(5000))
      await vi.waitFor(() => expect(result.current.data?.releases[0].status).toBe('PUBLISHED'))
      await act(() => vi.advanceTimersByTimeAsync(15_000))
      expect(fetchJson).toHaveBeenCalledTimes(2)
    } finally { unmount(); client.clear() }
  })

  it.each([
    ['PUBLISHED', 'SUCCEEDED'],
    ['YANKED', 'SUCCEEDED'],
    ['PENDING_REVIEW', 'FAILED'],
    ['PENDING_REVIEW', undefined],
  ])('does not keep polling a settled or unapproved release: %s / %s', async (status, publicationStatus) => {
    fetchJson.mockResolvedValue(snapshot(status, publicationStatus))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
    const { unmount } = renderHook(() => usePluginProject(39), { wrapper: wrapper(client) })
    try {
      await vi.waitFor(() => expect(fetchJson).toHaveBeenCalledTimes(1))
      await act(() => vi.advanceTimersByTimeAsync(20_000))
      expect(fetchJson).toHaveBeenCalledTimes(1)
    } finally { unmount(); client.clear() }
  })
})
