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
        params && typeof params === 'object' && 'revision' in params ? `${key}:${params.revision}` : key,
      i18n: { language: 'en' },
    }),
  }
})

vi.mock('@/shared/lib/date-time', () => ({
  formatLocalDateTime: (value: string) => value,
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: (props: Record<string, unknown>) => <input {...props} />,
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

vi.mock('@/shared/ui/table', () => ({
  Table: ({ children }: { children: ReactNode }) => children,
  TableBody: ({ children }: { children: ReactNode }) => children,
  TableCell: ({ children }: { children: ReactNode }) => children,
  TableHead: ({ children }: { children: ReactNode }) => children,
  TableHeader: ({ children }: { children: ReactNode }) => children,
  TableRow: ({ children }: { children: ReactNode }) => children,
}))

vi.mock('@/shared/ui/dialog', () => ({
  // honor `open` so the closed rollback dialog keeps the main form unambiguous
  Dialog: ({ children, open }: { children: ReactNode; open?: boolean }) =>
    open === false ? null : children,
  DialogContent: ({ children }: { children: ReactNode }) => children,
  DialogDescription: ({ children }: { children: ReactNode }) => children,
  DialogFooter: ({ children }: { children: ReactNode }) => children,
  DialogHeader: ({ children }: { children: ReactNode }) => children,
  DialogTitle: ({ children }: { children: ReactNode }) => children,
}))

const hooks = vi.hoisted(() => ({
  useSoftwareSettings: vi.fn(),
  useSoftwareSettingsHistory: vi.fn(),
  useValidateSoftwareSettings: vi.fn(),
  useUpdateSoftwareSettings: vi.fn(),
  useRollbackSoftwareSettings: vi.fn(),
}))

vi.mock('@/features/software/use-software-queries', () => ({
  useSoftwareSettings: hooks.useSoftwareSettings,
  useSoftwareSettingsHistory: hooks.useSoftwareSettingsHistory,
  useValidateSoftwareSettings: hooks.useValidateSoftwareSettings,
  useUpdateSoftwareSettings: hooks.useUpdateSoftwareSettings,
  useRollbackSoftwareSettings: hooks.useRollbackSoftwareSettings,
}))

import { SoftwareSettingsPage } from './software-settings'
import { toast } from '@/shared/lib/toast'

const config = {
  maxPackageBytes: 5368709120,
  totalQuotaBytes: 549755813888,
  maxActiveUploadsPerAdmin: 2,
  chunkSizeBytes: 8388608,
  uploadIdleTimeoutMinutes: 1440,
  failedTempRetentionHours: 168,
  cleanupScanIntervalMinutes: 5,
  maxConcurrentInspectionsPerWorker: 1,
  inspectionTotalTimeoutSeconds: 900,
  inspectionSubprocessTimeoutSeconds: 60,
  inspectionSoftMemoryLimitBytes: 536870912,
  inspectionMaxExtractedBytes: 21474836480,
  inspectionMaxExtractedEntries: 10000,
  maxAutoInspectionAttempts: 3,
  downloadSessionIdleTimeoutMinutes: 1440,
}

const settingsResponse = {
  revision: 2,
  schemaVersion: 1,
  config,
  deploymentLimits: {},
  changedBy: 'admin',
  changedAt: '2026-01-01T00:00:00Z',
}

describe('SoftwareSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    hooks.useSoftwareSettings.mockReturnValue({ data: settingsResponse, isLoading: false })
    hooks.useSoftwareSettingsHistory.mockReturnValue({
      data: [
        { revision: 2, schemaVersion: 1, config, changedBy: 'admin', changeReason: 'tune', createdAt: '2026-01-02T00:00:00Z' },
        { revision: 1, schemaVersion: 1, config, changedBy: 'bootstrap', changeReason: 'init', createdAt: '2026-01-01T00:00:00Z' },
      ],
    })
    hooks.useValidateSoftwareSettings.mockReturnValue({
      mutate: vi.fn((_config: unknown, options?: { onSuccess?: (result: unknown) => void }) =>
        options?.onSuccess?.({ valid: true, violations: [] })),
      isPending: false,
    })
    hooks.useUpdateSoftwareSettings.mockReturnValue({ mutate: vi.fn(), isPending: false })
    hooks.useRollbackSoftwareSettings.mockReturnValue({ mutate: vi.fn(), isPending: false })
  })

  afterEach(() => {
    cleanup()
  })

  it('renders every policy field with the current revision', () => {
    const html = renderToStaticMarkup(<SoftwareSettingsPage />)

    for (const key of Object.keys(config)) {
      expect(html).toContain(`softwareSettings.fields.${key}`)
    }
    expect(html).toContain('#2')
  })

  it('saves with the expected revision and change reason', () => {
    const update = vi.fn()
    hooks.useUpdateSoftwareSettings.mockReturnValue({ mutate: update, isPending: false })

    const view = render(<SoftwareSettingsPage />)
    fireEvent.change(view.getByLabelText('softwareSettings.changeReason'), {
      target: { value: 'raise quota' },
    })
    fireEvent.click(view.getByRole('button', { name: 'softwareSettings.save' }))

    expect(update).toHaveBeenCalledWith(
      { expectedRevision: 2, changeReason: 'raise quota', config },
      expect.anything(),
    )
  })

  it('shows field violations returned by validation', () => {
    hooks.useValidateSoftwareSettings.mockReturnValue({
      mutate: vi.fn((_config: unknown, options?: { onSuccess?: (result: unknown) => void }) =>
        options?.onSuccess?.({
          valid: false,
          violations: [{ field: 'chunkSizeBytes', messageCode: 'error.software.policy.chunk', messageArgs: [] }],
        })),
      isPending: false,
    })

    const view = render(<SoftwareSettingsPage />)
    fireEvent.click(view.getByRole('button', { name: 'softwareSettings.validate' }))

    expect(view.getByText('error.software.policy.chunk')).toBeTruthy()
  })

  it('surfaces update failures as an error toast', () => {
    hooks.useUpdateSoftwareSettings.mockReturnValue({
      mutate: vi.fn((_input: unknown, options?: { onError?: (error: unknown) => void }) =>
        options?.onError?.(new Error('revision conflict'))),
      isPending: false,
    })

    const view = render(<SoftwareSettingsPage />)
    fireEvent.change(view.getByLabelText('softwareSettings.changeReason'), {
      target: { value: 'raise quota' },
    })
    fireEvent.click(view.getByRole('button', { name: 'softwareSettings.save' }))

    expect(toast.error).toHaveBeenCalledWith('revision conflict')
  })

  it('rolls a historical revision back with a reason', () => {
    const rollback = vi.fn()
    hooks.useRollbackSoftwareSettings.mockReturnValue({ mutate: rollback, isPending: false })

    const view = render(<SoftwareSettingsPage />)
    // only the revision-1 row offers rollback; the current revision shows a label instead
    fireEvent.click(view.getByRole('button', { name: 'softwareSettings.rollback' }))
    const rollbackReason = view.container.querySelector('#software-rollback-reason') as HTMLInputElement
    fireEvent.change(rollbackReason, { target: { value: 'undo' } })
    // the row button and the dialog submit share the label; the dialog one is last
    const rollbackButtons = view.getAllByRole('button', { name: 'softwareSettings.rollback' })
    fireEvent.click(rollbackButtons[rollbackButtons.length - 1])

    expect(rollback).toHaveBeenCalledWith(
      { fromRevision: 1, changeReason: 'undo' },
      expect.anything(),
    )
  })
})
