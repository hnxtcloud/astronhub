// @vitest-environment jsdom

import { cleanup, fireEvent, render } from '@testing-library/react'
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key }),
  }
})

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: (props: Record<string, unknown>) => <input {...props} />,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, onClick }: { children: ReactNode; onClick?: () => void }) => (
    <button type="button" onClick={onClick}>{children}</button>
  ),
}))

vi.mock('@/shared/components/pagination', () => ({
  Pagination: ({ page }: { page: number }) => <div>pagination:{page}</div>,
}))

const useSoftwareCategories = vi.fn()
const usePublishedApps = vi.fn()
const publishedAppsCalls: Array<Record<string, unknown>> = []
vi.mock('@/features/software/use-software-queries', () => ({
  useSoftwareCategories: () => useSoftwareCategories(),
  usePublishedApps: (params: Record<string, unknown>) => {
    publishedAppsCalls.push(params)
    return usePublishedApps()
  },
}))

import { SoftwareCatalogPage } from './catalog'

const app = {
  id: 1,
  slug: 'demo-app',
  name: 'Demo App',
  description: 'A demo application',
  iconObjectKey: null,
  categoryCode: 'dev',
  categoryName: 'Development',
  createdAt: '2026-01-01T00:00:00Z',
}

describe('SoftwareCatalogPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    publishedAppsCalls.length = 0
    useSoftwareCategories.mockReturnValue({
      data: [{ id: 1, code: 'dev', name: 'Development', sortOrder: 0 }],
    })
    usePublishedApps.mockReturnValue({
      data: { items: [app], total: 1, page: 0, size: 20 },
      isLoading: false,
    })
  })

  afterEach(() => {
    cleanup()
  })

  it('renders catalog apps as cards with their category', () => {
    const html = renderToStaticMarkup(<SoftwareCatalogPage />)

    expect(publishedAppsCalls[0]).toEqual({
      keyword: undefined,
      categoryCode: undefined,
      page: 0,
      size: 20,
    })
    expect(html).toContain('Demo App')
    expect(html).toContain('Development')
  })

  it('commits the keyword on Enter and resets to the first page', () => {
    const view = render(<SoftwareCatalogPage />)

    fireEvent.change(view.getByPlaceholderText('softwareCatalog.searchPlaceholder'), {
      target: { value: 'demo' },
    })
    fireEvent.keyDown(view.getByPlaceholderText('softwareCatalog.searchPlaceholder'), {
      key: 'Enter',
    })

    expect(publishedAppsCalls[publishedAppsCalls.length - 1]).toMatchObject({ keyword: 'demo' })
  })

  it('filters by category and clears with the all-categories button', () => {
    const view = render(<SoftwareCatalogPage />)

    fireEvent.click(view.getByRole('button', { name: 'Development' }))
    expect(publishedAppsCalls[publishedAppsCalls.length - 1]).toMatchObject({
      categoryCode: 'dev',
      page: 0,
    })

    fireEvent.click(view.getByRole('button', { name: 'softwareCatalog.allCategories' }))
    expect(publishedAppsCalls[publishedAppsCalls.length - 1]).toMatchObject({ categoryCode: undefined })
  })

  it('renders loading and empty states without cards', () => {
    usePublishedApps
      .mockReturnValueOnce({ data: undefined, isLoading: true })
      .mockReturnValueOnce({ data: { items: [], total: 0, page: 0, size: 20 }, isLoading: false })

    expect(renderToStaticMarkup(<SoftwareCatalogPage />)).toContain('common.loading')
    expect(renderToStaticMarkup(<SoftwareCatalogPage />)).toContain('softwareCatalog.empty')
  })
})
