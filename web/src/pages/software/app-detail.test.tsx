// @vitest-environment jsdom

import { cleanup, render } from '@testing-library/react'
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  useParams: () => ({ slug: 'demo-app' }),
}))

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (value: string) => value,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/ui/button', async () => ({
  ...await vi.importActual<typeof import('@/shared/ui/button')>('@/shared/ui/button'),
  Button: ({ children, onClick, disabled }: {
    children: ReactNode
    onClick?: () => void
    disabled?: boolean
  }) => (
    <button type="button" onClick={onClick} disabled={disabled}>{children}</button>
  ),
}))

vi.mock('@/shared/ui/table', () => ({
  Table: ({ children }: { children: ReactNode }) => children,
  TableBody: ({ children }: { children: ReactNode }) => children,
  TableCell: ({ children }: { children: ReactNode }) => children,
  TableHead: ({ children }: { children: ReactNode }) => children,
  TableHeader: ({ children }: { children: ReactNode }) => children,
  TableRow: ({ children }: { children: ReactNode }) => children,
}))

const useAppDetail = vi.fn()
const useCreateDownloadSession = vi.fn()
vi.mock('@/features/software/use-software-queries', () => ({
  useAppDetail: (slug: string | undefined) => useAppDetail(slug),
  useCreateDownloadSession: () => useCreateDownloadSession(),
}))

import { SoftwareAppDetailPage } from './app-detail'

const detail = {
  id: 7,
  slug: 'demo-app',
  name: 'Demo App',
  description: 'A demo application',
  iconObjectKey: null,
  categoryCode: 'dev',
  categoryName: 'Development',
  status: 'PUBLISHED',
  createdAt: '2026-01-01T00:00:00Z',
  releases: [
    {
      id: 3,
      displayVersion: '1.0.0',
      releaseNotes: 'First release',
      publishedAt: '2026-02-01T00:00:00Z',
      packageCount: 1,
      packages: [
        {
          id: 11,
          revisionNo: 1,
          status: 'PUBLISHED',
          targetOs: 'windows',
          targetArch: 'x86_64',
          targetDistro: null,
          format: 'msi',
          originalFullVersion: '1.0.0.42',
          fileName: 'demo-1.0.0.msi',
          byteSize: 5 * 1024 * 1024,
          sha256: 'abc',
          applicabilityNote: null,
          confirmedBy: 'admin',
          confirmedAt: '2026-02-01T00:00:00Z',
          publishedAt: '2026-02-01T00:00:00Z',
        },
        {
          id: 12,
          revisionNo: 1,
          status: 'TAKEN_DOWN',
          targetOs: 'linux',
          targetArch: 'arm64',
          targetDistro: null,
          format: 'deb',
          originalFullVersion: '1.0.0.42',
          fileName: 'demo-1.0.0.deb',
          byteSize: 4 * 1024 * 1024,
          sha256: 'def',
          applicabilityNote: null,
          confirmedBy: 'admin',
          confirmedAt: '2026-02-01T00:00:00Z',
          publishedAt: null,
        },
      ],
      recommendations: [],
    },
  ],
}

describe('SoftwareAppDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAppDetail.mockReturnValue({ data: detail, isLoading: false })
    useCreateDownloadSession.mockReturnValue({
      // hash-only URL: jsdom performs hash navigation natively, letting the
      // test observe the redirect without spying on location.assign
      mutateAsync: vi.fn().mockResolvedValue({ downloadUrl: '#download-token' }),
      isPending: false,
    })
  })

  afterEach(() => {
    cleanup()
  })

  it('renders only published packages per release', () => {
    const html = renderToStaticMarkup(<SoftwareAppDetailPage />)

    expect(useAppDetail).toHaveBeenCalledWith('demo-app')
    expect(html).toContain('Demo App')
    expect(html).toContain('demo-1.0.0.msi')
    expect(html).not.toContain('demo-1.0.0.deb')
    expect(html).toContain('windows/x86_64')
  })

  it('navigates to the download session URL when downloading', async () => {
    const mutateAsync = vi.fn().mockResolvedValue({ downloadUrl: '#download-token' })
    useCreateDownloadSession.mockReturnValue({ mutateAsync, isPending: false })

    const view = render(<SoftwareAppDetailPage />)
    const { fireEvent } = await import('@testing-library/react')
    fireEvent.click(view.getByRole('button', { name: 'softwareAppDetail.download' }))

    await vi.waitFor(() => {
      expect(mutateAsync).toHaveBeenCalledWith(11)
    })
    await vi.waitFor(() => {
      expect(window.location.hash).toBe('#download-token')
    })
  })

  it('renders loading and not-found states', () => {
    useAppDetail
      .mockReturnValueOnce({ data: undefined, isLoading: true })
      .mockReturnValueOnce({ data: undefined, isLoading: false })

    expect(renderToStaticMarkup(<SoftwareAppDetailPage />)).toContain('common.loading')
    expect(renderToStaticMarkup(<SoftwareAppDetailPage />)).toContain('softwareAppDetail.notFound')
  })
})
