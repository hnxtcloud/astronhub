// @vitest-environment jsdom

import { cleanup, fireEvent, render } from '@testing-library/react'
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  useCatalog: vi.fn(),
  queries: [] as Array<{ type: string; query: string }>,
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

vi.mock('@/features/catalog-search/use-capability-catalog', () => ({
  useCapabilityCatalog: (type: string, query: string) => {
    mocks.queries.push({ type, query })
    return mocks.useCatalog()
  },
}))

vi.mock('@/entities/plugin/plugin-card', () => ({
  PluginCard: ({ item }: { item: { displayName: string } }) => <div>plugin:{item.displayName}</div>,
}))

vi.mock('@/entities/mcp-server/mcp-server-card', () => ({
  McpServerCard: ({ item }: { item: { displayName: string } }) => <div>mcp:{item.displayName}</div>,
}))

import { CatalogPage, McpCatalogPage, PluginCatalogPage } from './capability-catalog'

const baseItem = {
  id: 1,
  namespace: 'global',
  slug: 'demo',
  summary: 'summary',
  visibility: 'PUBLIC',
  status: 'ACTIVE',
  targets: [],
  primaryMetric: 0,
}

describe('CapabilityCatalogPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.queries.length = 0
    mocks.useCatalog.mockReturnValue({
      data: {
        items: [
          { ...baseItem, type: 'PLUGIN', coordinate: 'plugin:@global/demo', displayName: 'Plugin Demo' },
          { ...baseItem, type: 'MCP', coordinate: 'mcp:@global/demo', displayName: 'MCP Demo' },
          { ...baseItem, type: 'SKILL', coordinate: 'skill:@global/demo', displayName: 'Skill Demo' },
        ],
      },
      isLoading: false,
      isError: false,
    })
  })

  afterEach(() => {
    cleanup()
  })

  it('renders each sibling capability with its matching presentation', () => {
    const html = renderToStaticMarkup(<CatalogPage />)

    expect(mocks.queries[0]).toEqual({ type: 'ALL', query: '' })
    expect(html).toContain('plugin:Plugin Demo')
    expect(html).toContain('mcp:MCP Demo')
    expect(html).toContain('Skill Demo')
  })

  it('starts dedicated routes with their domain filter', () => {
    renderToStaticMarkup(<PluginCatalogPage />)
    renderToStaticMarkup(<McpCatalogPage />)

    expect(mocks.queries).toEqual([
      { type: 'PLUGIN', query: '' },
      { type: 'MCP', query: '' },
    ])
  })

  it('updates the query and type filter from user input', () => {
    const view = render(<CatalogPage />)

    fireEvent.change(view.getByPlaceholderText('catalog.searchPlaceholder'), {
      target: { value: 'review' },
    })
    fireEvent.click(view.getByRole('button', { name: 'catalog.types.plugin' }))

    expect(mocks.queries).toContainEqual({ type: 'ALL', query: 'review' })
    expect(mocks.queries[mocks.queries.length - 1]).toEqual({ type: 'PLUGIN', query: 'review' })
  })

  it('renders loading, error, and empty states without cards', () => {
    mocks.useCatalog
      .mockReturnValueOnce({ data: undefined, isLoading: true, isError: false })
      .mockReturnValueOnce({ data: undefined, isLoading: false, isError: true })
      .mockReturnValueOnce({ data: { items: [] }, isLoading: false, isError: false })

    expect(renderToStaticMarkup(<CatalogPage />)).toContain('catalog.loading')
    expect(renderToStaticMarkup(<CatalogPage />)).toContain('catalog.error')
    expect(renderToStaticMarkup(<CatalogPage />)).toContain('catalog.empty')
  })
})
