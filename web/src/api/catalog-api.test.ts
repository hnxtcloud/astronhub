import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('openapi-fetch', () => ({
  default: () => ({ GET: (...args: unknown[]) => mocks.get(...args) }),
}))

vi.mock('@/i18n/config', () => ({
  default: {
    resolvedLanguage: 'en',
    exists: () => false,
    t: (key: string) => key,
  },
}))

import { catalogApi } from './client'

describe('catalogApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses the generated endpoint and normalizes optional page fields', async () => {
    mocks.get.mockResolvedValue({
      response: { ok: true, status: 200 },
      error: undefined,
      data: {
        code: 0,
        msg: 'ok',
        data: {
          items: [{
            type: 'PLUGIN',
            id: 7,
            coordinate: 'plugin:@global/review',
            namespace: 'global',
            slug: 'review',
            displayName: 'Review Plugin',
            visibility: 'PUBLIC',
            status: 'ACTIVE',
          }],
          total: 1,
        },
      },
    })

    await expect(catalogApi.search({ type: 'PLUGIN', q: 'review', size: 40 })).resolves.toEqual({
      items: [expect.objectContaining({ targets: [], primaryMetric: 0 })],
      total: 1,
      page: 0,
      size: 40,
    })
    expect(mocks.get).toHaveBeenCalledWith('/api/v1/catalog/search', expect.objectContaining({
      params: { query: { type: 'PLUGIN', q: 'review', page: 0, size: 40 } },
    }))
  })

  it('drops malformed items and surfaces API failures', async () => {
    mocks.get.mockResolvedValueOnce({
      response: { ok: true, status: 200 },
      error: undefined,
      data: {
        code: 0,
        msg: 'ok',
        data: { items: [{ type: 'PLUGIN', id: 1 }], total: 1, page: 0, size: 20 },
      },
    })

    await expect(catalogApi.search({ type: 'ALL' })).resolves.toMatchObject({ items: [] })

    mocks.get.mockResolvedValueOnce({
      response: { ok: false, status: 503 },
      error: { code: 503 },
      data: undefined,
    })
    await expect(catalogApi.search({ type: 'MCP' })).rejects.toMatchObject({ status: 503 })
  })
})
