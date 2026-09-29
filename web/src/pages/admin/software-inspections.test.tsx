// @vitest-environment jsdom

import { cleanup, render, fireEvent } from '@testing-library/react'
import { renderToStaticMarkup } from 'react-dom/server'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, params?: Record<string, unknown>) =>
        params && typeof params === 'object' && 'taskId' in params ? `${key}:${params.taskId}` : key,
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

vi.mock('@/shared/ui/textarea', () => ({
  Textarea: (props: Record<string, unknown>) => <textarea {...props} />,
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
  useSoftwareInspections: vi.fn(),
  useSoftwareInspectionDetail: vi.fn(),
  useRetryInspection: vi.fn(),
  useConfirmInspectionMetadata: vi.fn(),
}))

vi.mock('@/features/software/use-software-queries', () => ({
  useSoftwareInspections: hooks.useSoftwareInspections,
  useSoftwareInspectionDetail: hooks.useSoftwareInspectionDetail,
  useRetryInspection: hooks.useRetryInspection,
  useConfirmInspectionMetadata: hooks.useConfirmInspectionMetadata,
}))

import { SoftwareInspectionsPage } from './software-inspections'

const task = {
  taskId: 42,
  packageRevisionId: 11,
  applicationId: 7,
  status: 'METADATA_FAILED',
  attemptCount: 2,
  maxAttempts: 3,
  manualRetryCount: 0,
  policyRevision: 4,
  format: 'msi',
  fileName: 'demo-1.0.0.msi',
  originalFullVersion: '1.0.0.42',
  byteSize: 5 * 1024 * 1024,
  leaseOwner: null,
  leaseExpiresAt: null,
  nextAttemptNotBefore: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-02T00:00:00Z',
}

describe('SoftwareInspectionsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    hooks.useSoftwareInspections.mockReturnValue({
      data: { items: [task], total: 1, page: 0, size: 20 },
      isLoading: false,
    })
    hooks.useSoftwareInspectionDetail.mockReturnValue({
      data: {
        task,
        attempts: [
          {
            attemptNo: 1,
            trigger: 'AUTO',
            outcome: 'METADATA_FAILED',
            errorCode: 'parser.noVersionResource',
            rawVersionFields: null,
            parserName: 'msi',
            parserVersion: '1',
            startedAt: '2026-01-01T00:00:00Z',
            finishedAt: '2026-01-01T00:01:00Z',
          },
        ],
      },
      isLoading: false,
    })
    hooks.useRetryInspection.mockReturnValue({ mutate: vi.fn(), isPending: false })
    hooks.useConfirmInspectionMetadata.mockReturnValue({ mutate: vi.fn(), isPending: false })
  })

  afterEach(() => {
    cleanup()
  })

  it('renders failed tasks with confirm-metadata and retry affordances', () => {
    const html = renderToStaticMarkup(<SoftwareInspectionsPage />)

    expect(hooks.useSoftwareInspections).toHaveBeenCalledWith({
      status: undefined,
      page: 0,
      size: 20,
    })
    expect(html).toContain('demo-1.0.0.msi')
    expect(html).toContain('softwareInspections.confirmMetadata')
    expect(html).toContain('softwareInspections.retry')
  })

  it('hides retry for leased and succeeded tasks', () => {
    hooks.useSoftwareInspections.mockReturnValue({
      data: {
        items: [
          { ...task, taskId: 43, status: 'LEASED' },
          { ...task, taskId: 44, status: 'SUCCEEDED' },
        ],
        total: 2, page: 0, size: 20,
      },
      isLoading: false,
    })

    const html = renderToStaticMarkup(<SoftwareInspectionsPage />)

    expect(html).toContain('softwareInspections.status.LEASED')
    expect(html).toContain('softwareInspections.status.SUCCEEDED')
    expect(html).not.toContain('softwareInspections.retry')
  })

  it('shows the attempt history in the detail dialog', () => {
    const view = render(<SoftwareInspectionsPage />)
    fireEvent.click(view.getByRole('button', { name: 'softwareInspections.detail' }))

    // mocked table parts flatten cells into text nodes; match on text content
    expect(view.container.textContent).toContain('parser.noVersionResource')
    expect(hooks.useSoftwareInspectionDetail).toHaveBeenCalledWith(42)
  })

  it('confirms metadata with the parsed fill-in fields', () => {
    const confirm = vi.fn()
    hooks.useConfirmInspectionMetadata.mockReturnValue({ mutate: confirm, isPending: false })

    const view = render(<SoftwareInspectionsPage />)
    fireEvent.click(view.getByRole('button', { name: 'softwareInspections.confirmMetadata' }))
    const textarea = view.container.querySelector('#software-inspection-raw-fields') as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: '{"ProductVersion": "1.0.0"}' } })
    // the row action and the dialog submit share the label; the dialog one is last
    const dialogSubmit = view.getAllByRole('button', { name: 'softwareInspections.confirmMetadata' })
    fireEvent.click(dialogSubmit[dialogSubmit.length - 1])

    expect(confirm).toHaveBeenCalledWith(
      { taskId: 42, rawVersionFields: { ProductVersion: '1.0.0' } },
      expect.anything(),
    )
  })
})
