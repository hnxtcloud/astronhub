import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  useQuery: vi.fn(),
  search: vi.fn(),
}))

vi.mock('@tanstack/react-query', () => ({
  useQuery: (options: unknown) => mocks.useQuery(options),
}))

vi.mock('@/api/client', () => ({
  catalogApi: {
    search: (...args: unknown[]) => mocks.search(...args),
  },
}))

import { useCapabilityCatalog } from './use-capability-catalog'

describe('useCapabilityCatalog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useQuery.mockImplementation((options: unknown) => options)
  })

  it('keys and executes a typed catalog query', async () => {
    const page = { items: [], total: 0, page: 0, size: 40 }
    mocks.search.mockResolvedValue(page)

    useCapabilityCatalog('PLUGIN', 'review')

    const options = mocks.useQuery.mock.calls[0]?.[0] as {
      queryKey: unknown
      queryFn: () => Promise<typeof page>
    }
    expect(options.queryKey).toEqual(['capability-catalog', 'PLUGIN', 'review'])
    await expect(options.queryFn()).resolves.toEqual(page)
    expect(mocks.search).toHaveBeenCalledWith({
      type: 'PLUGIN',
      q: 'review',
      page: 0,
      size: 40,
    })
  })
})
