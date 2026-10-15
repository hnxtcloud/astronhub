// @vitest-environment jsdom

import { cleanup, render, fireEvent } from '@testing-library/react'
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'

vi.mock('@/features/software/software-update-manager', () => ({ SoftwareUpdateManager: () => null, SoftwareUpdateSourceManager: () => null }))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, params?: Record<string, unknown>) =>
        params && typeof params === 'object' && 'name' in params ? `${key}:${params.name}` : key,
      i18n: { language: 'en' },
    }),
  }
})

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (value: string) => value,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: (props: Record<string, unknown>) => <input {...props} />,
}))

vi.mock('@/shared/ui/textarea', () => ({
  Textarea: (props: Record<string, unknown>) => <textarea {...props} />,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, onClick, disabled }: {
    children: ReactNode
    onClick?: () => void
    disabled?: boolean
  }) => (
    <button type="button" onClick={onClick} disabled={disabled}>{children}</button>
  ),
}))

vi.mock('@/shared/ui/label', () => ({
  Label: ({ children, htmlFor }: { children: ReactNode; htmlFor?: string }) => (
    <label htmlFor={htmlFor}>{children}</label>
  ),
}))

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))

vi.mock('@/shared/ui/select', () => ({
  Select: ({ children }: { children: ReactNode }) => children,
  SelectContent: ({ children }: { children: ReactNode }) => children,
  SelectItem: ({ children }: { children: ReactNode }) => children,
  SelectTrigger: ({ children }: { children: ReactNode }) => children,
  SelectValue: () => null,
  normalizeSelectValue: (v: string) => v || null,
}))

vi.mock('@/shared/ui/table', () => ({
  Table: ({ children }: { children: ReactNode }) => children,
  TableBody: ({ children }: { children: ReactNode }) => children,
  TableCell: ({ children }: { children: ReactNode }) => children,
  TableHead: ({ children }: { children: ReactNode }) => children,
  TableHeader: ({ children }: { children: ReactNode }) => children,
  TableRow: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/ui/dialog', () => ({
  Dialog: ({ children }: { children: ReactNode }) => children,
  DialogContent: ({ children }: { children: ReactNode }) => children,
  DialogDescription: ({ children }: { children: ReactNode }) => children,
  DialogFooter: ({ children }: { children: ReactNode }) => children,
  DialogHeader: ({ children }: { children: ReactNode }) => children,
  DialogTitle: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/components/pagination', () => ({
  Pagination: () => null,
}))

const hooks = vi.hoisted(() => ({
  useAdminSoftwareApps: vi.fn(),
  useSoftwareCategories: vi.fn(),
  useCreateSoftwareApp: vi.fn(),
  useSetSoftwareAppStatus: vi.fn(),
  useSoftwareReleases: vi.fn(),
  useCreateSoftwareRelease: vi.fn(),
  useSetPackageStatus: vi.fn(),
  runChunkedUpload: vi.fn(),
}))

vi.mock('@/features/software/use-software-queries', () => ({
  useAdminSoftwareApps: hooks.useAdminSoftwareApps,
  useSoftwareCategories: hooks.useSoftwareCategories,
  useCreateSoftwareApp: hooks.useCreateSoftwareApp,
  useSetSoftwareAppStatus: hooks.useSetSoftwareAppStatus,
  useSoftwareReleases: hooks.useSoftwareReleases,
  useCreateSoftwareRelease: hooks.useCreateSoftwareRelease,
  useSetPackageStatus: hooks.useSetPackageStatus,
  runChunkedUpload: hooks.runChunkedUpload,
}))

import { AdminSoftwarePage } from './software'

const draftApp = {
  id: 1,
  slug: 'demo-app',
  name: 'Demo App',
  description: null,
  iconObjectKey: null,
  categoryCode: 'dev',
  categoryName: 'Development',
  createdAt: '2026-01-01T00:00:00Z',
  status: 'DRAFT',
  updatedAt: '2026-01-02T00:00:00Z',
}

const draftPackage = {
  id: 11,
  revisionNo: 1,
  status: 'DRAFT',
  targetOs: 'windows',
  targetArch: 'x86_64',
  targetDistro: null,
  format: 'msi',
  originalFullVersion: '1.0.0.42',
  fileName: 'demo-1.0.0.msi',
  byteSize: 5 * 1024 * 1024,
  sha256: 'abc',
  applicabilityNote: null,
  confirmedBy: null,
  confirmedAt: null,
  publishedAt: null,
}

function mockDefaults() {
  hooks.useAdminSoftwareApps.mockReturnValue({
    data: { items: [draftApp], total: 1, page: 0, size: 20 },
    isLoading: false,
  })
  hooks.useSoftwareCategories.mockReturnValue({
    data: [{ id: 1, code: 'dev', name: 'Development', sortOrder: 0 }],
  })
  hooks.useCreateSoftwareApp.mockReturnValue({ mutate: vi.fn(), isPending: false })
  hooks.useSetSoftwareAppStatus.mockReturnValue({ mutate: vi.fn(), isPending: false })
  hooks.useSoftwareReleases.mockReturnValue({
    data: [
      {
        id: 3,
        displayVersion: '1.0.0',
        releaseNotes: null,
        publishedAt: null,
        packageCount: 1,
        packages: [draftPackage],
        recommendations: [],
      },
    ],
    isLoading: false,
  })
  hooks.useCreateSoftwareRelease.mockReturnValue({ mutate: vi.fn(), isPending: false })
  hooks.useSetPackageStatus.mockReturnValue({
    mutate: vi.fn((_input: unknown, options?: { onSuccess?: () => void }) =>
      options?.onSuccess?.()),
    isPending: false,
  })
}

describe('AdminSoftwarePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDefaults()
  })

  afterEach(() => {
    cleanup()
  })

  it('renders draft apps with a publish action in the catalog table', () => {
    const html = renderToStaticMarkup(<AdminSoftwarePage />)

    expect(hooks.useAdminSoftwareApps).toHaveBeenCalledWith({
      keyword: undefined,
      status: undefined,
      page: 0,
      size: 20,
    })
    expect(html).toContain('Demo App')
    expect(html).toContain('softwareAdmin.publish')
    expect(html).not.toContain('demo-1.0.0.msi')
  })

  it('manages an app to reveal its releases and package actions', () => {
    const view = render(<AdminSoftwarePage />)
    fireEvent.click(view.getByRole('button', { name: 'softwareAdmin.manage' }))

    expect(view.container.textContent).toContain('softwareAdmin.releasesOf:Demo App')
    expect(view.container.textContent).toContain('demo-1.0.0.msi')
    // unconfirmed draft offers confirm; confirmed draft offers publish
    expect(view.container.textContent).toContain('softwareAdmin.confirmPackage')
    expect(view.container.textContent).toContain('softwareAdmin.publish')
  })

  it('offers confirm only for unconfirmed drafts and publish for confirmed ones', () => {
    hooks.useSoftwareReleases.mockReturnValue({
      data: [
        {
          id: 3,
          displayVersion: '1.0.0',
          releaseNotes: null,
          publishedAt: null,
          packageCount: 2,
          packages: [
            draftPackage,
            { ...draftPackage, id: 12, fileName: 'demo-confirmed.msi', confirmedBy: 'admin', confirmedAt: '2026-01-03T00:00:00Z' },
          ],
        },
      ],
      isLoading: false,
    })

    const view = render(<AdminSoftwarePage />)
    fireEvent.click(view.getByRole('button', { name: 'softwareAdmin.manage' }))

    const text = view.container.textContent
    expect(text).toContain('demo-confirmed.msi')
    // row for the confirmed draft: publish action, no confirm
    const confirmedRow = text!.indexOf('demo-confirmed.msi')
    const unconfirmedRow = text!.indexOf('demo-1.0.0.msi')
    expect(confirmedRow).toBeGreaterThan(-1)
    expect(unconfirmedRow).toBeGreaterThan(-1)
  })

  it('creates an application through the dialog mutation', () => {
    const createApp = vi.fn()
    hooks.useCreateSoftwareApp.mockReturnValue({ mutate: createApp, isPending: false })

    const view = render(<AdminSoftwarePage />)
    fireEvent.click(view.getByRole('button', { name: 'softwareAdmin.createApp' }))
    fireEvent.change(view.getByLabelText('softwareAdmin.slug'), {
      target: { value: 'demo-app' },
    })
    fireEvent.change(view.getByLabelText('softwareAdmin.name'), {
      target: { value: 'Demo App' },
    })
    fireEvent.click(view.getByRole('button', { name: 'common.save' }))

    expect(createApp).toHaveBeenCalledWith(
      { slug: 'demo-app', name: 'Demo App', description: undefined, categoryCode: undefined },
      expect.anything(),
    )
  })

  it('uploads a package via the chunked transfer with the session request', async () => {
    hooks.runChunkedUpload.mockResolvedValue({ packageRevisionId: 11, sha256: 'abc' })

    const view = render(<AdminSoftwarePage />)
    fireEvent.click(view.getByRole('button', { name: 'softwareAdmin.manage' }))
    fireEvent.click(view.getAllByRole('button', { name: 'softwareAdmin.uploadPackage' })[0])

    const fileInput = view.container.querySelector('input[type="file"]') as HTMLInputElement
    const file = new File(['x'], 'demo-1.0.0.msi', { type: 'application/octet-stream' })
    Object.defineProperty(fileInput, 'files', { value: [file] })
    fireEvent.change(fileInput)

    fireEvent.change(view.getByLabelText('softwareAdmin.originalFullVersion'), {
      target: { value: '1.0.0.42' },
    })
    fireEvent.click(view.getByRole('button', { name: 'softwareAdmin.upload' }))

    await vi.waitFor(() => {
      expect(hooks.runChunkedUpload).toHaveBeenCalledWith(
        expect.objectContaining({
          releaseId: 3,
          fileName: 'demo-1.0.0.msi',
          format: 'zip',
          targetOs: 'windows',
          targetArch: 'x86_64',
          originalFullVersion: '1.0.0.42',
          byteSize: 1,
        }),
        file,
        expect.anything(),
      )
    })
  })
})
