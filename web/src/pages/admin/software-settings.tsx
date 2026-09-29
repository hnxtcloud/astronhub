import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import {
  useRollbackSoftwareSettings,
  useSoftwareSettings,
  useSoftwareSettingsHistory,
  useUpdateSoftwareSettings,
  useValidateSoftwareSettings,
} from '@/features/software/use-software-queries'
import type { SoftwarePolicyConfigDto } from '@/api/types'

type PolicyField = {
  key: keyof SoftwarePolicyConfigDto
  unitKey: string
}

/** Field order matches the spec's policy table; units come from i18n. */
const POLICY_FIELDS: PolicyField[] = [
  { key: 'maxPackageBytes', unitKey: 'bytes' },
  { key: 'totalQuotaBytes', unitKey: 'bytes' },
  { key: 'maxActiveUploadsPerAdmin', unitKey: 'count' },
  { key: 'chunkSizeBytes', unitKey: 'bytes' },
  { key: 'uploadIdleTimeoutMinutes', unitKey: 'minutes' },
  { key: 'failedTempRetentionHours', unitKey: 'hours' },
  { key: 'cleanupScanIntervalMinutes', unitKey: 'minutes' },
  { key: 'maxConcurrentInspectionsPerWorker', unitKey: 'count' },
  { key: 'inspectionTotalTimeoutSeconds', unitKey: 'seconds' },
  { key: 'inspectionSubprocessTimeoutSeconds', unitKey: 'seconds' },
  { key: 'inspectionSoftMemoryLimitBytes', unitKey: 'bytes' },
  { key: 'inspectionMaxExtractedBytes', unitKey: 'bytes' },
  { key: 'inspectionMaxExtractedEntries', unitKey: 'count' },
  { key: 'maxAutoInspectionAttempts', unitKey: 'count' },
  { key: 'downloadSessionIdleTimeoutMinutes', unitKey: 'minutes' },
]

/**
 * Dynamic policy settings. Saves are guarded by the expected revision
 * (optimistic locking) and a mandatory change reason; validation and the
 * revision history with rollback sit alongside the form.
 */
export function SoftwareSettingsPage() {
  const { t, i18n } = useTranslation()
  const settings = useSoftwareSettings()
  const history = useSoftwareSettingsHistory()
  const validate = useValidateSoftwareSettings()
  const update = useUpdateSoftwareSettings()
  const rollback = useRollbackSoftwareSettings()

  const [draft, setDraft] = useState<SoftwarePolicyConfigDto | null>(null)
  const [changeReason, setChangeReason] = useState('')
  const [violations, setViolations] = useState<{ field: string; messageCode: string }[]>([])
  const [rollbackTarget, setRollbackTarget] = useState<number | null>(null)
  const [rollbackReason, setRollbackReason] = useState('')

  // Render-phase initialization so the form is also correct in static renders;
  // a rollback resets the draft by setting it back to null.
  if (settings.data && draft == null) {
    setDraft(settings.data.config)
  }

  const setField = (key: keyof SoftwarePolicyConfigDto, value: string) => {
    setDraft((current) =>
      current ? { ...current, [key]: value === '' ? ('' as never) : Number(value) } : current,
    )
  }

  const handleValidate = () => {
    if (!draft) return
    validate.mutate(draft, {
      onSuccess: (result) => setViolations(result.violations),
      onError: (error) => toast.error(error instanceof Error ? error.message : String(error)),
    })
  }

  const handleSave = () => {
    if (!settings.data || !draft || changeReason.trim() === '') return
    update.mutate(
      { expectedRevision: settings.data.revision, changeReason: changeReason.trim(), config: draft },
      {
        onSuccess: () => {
          toast.success(t('softwareSettings.saveSuccess'))
          setChangeReason('')
          setViolations([])
        },
        onError: (error) =>
          toast.error(error instanceof Error ? error.message : String(error)),
      },
    )
  }

  const handleRollback = () => {
    if (rollbackTarget == null || rollbackReason.trim() === '') return
    rollback.mutate(
      { fromRevision: rollbackTarget, changeReason: rollbackReason.trim() },
      {
        onSuccess: () => {
          toast.success(t('softwareSettings.rollbackSuccess'))
          setRollbackTarget(null)
          setRollbackReason('')
          setDraft(null)
        },
        onError: (error) =>
          toast.error(error instanceof Error ? error.message : String(error)),
      },
    )
  }

  if (settings.isLoading || !settings.data || !draft) {
    return <p className="p-8 text-muted-foreground">{t('common.loading')}</p>
  }

  const conflict = update.isError

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">{t('softwareSettings.title')}</h1>
        <p className="text-sm text-muted-foreground">
          {t('softwareSettings.revision')} #{settings.data.revision} ·{' '}
          {t('softwareSettings.changedBy')} {settings.data.changedBy} ·{' '}
          {formatLocalDateTime(settings.data.changedAt, i18n.language)}
        </p>
      </div>

      {conflict ? (
        <Card className="border-destructive p-3 text-sm text-destructive">
          {t('softwareSettings.revisionConflict')}
        </Card>
      ) : null}

      <Card className="space-y-4 p-4">
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {POLICY_FIELDS.map((field) => (
            <div key={field.key} className="space-y-1">
              <Label htmlFor={`software-policy-${field.key}`}>
                {t(`softwareSettings.fields.${field.key}`)}
              </Label>
              <Input
                id={`software-policy-${field.key}`}
                type="number"
                min={0}
                value={String(draft[field.key] ?? '')}
                onChange={(event) => setField(field.key, event.target.value)}
              />
              <p className="text-xs text-muted-foreground">
                {t(`softwareSettings.units.${field.unitKey}`)}
              </p>
              {violations
                .filter((violation) => violation.field === field.key)
                .map((violation) => (
                  <p key={violation.messageCode} className="text-xs text-destructive">
                    {t(violation.messageCode)}
                  </p>
                ))}
            </div>
          ))}
        </div>

        <div className="space-y-2">
          <Label htmlFor="software-policy-reason">{t('softwareSettings.changeReason')}</Label>
          <Input
            id="software-policy-reason"
            value={changeReason}
            onChange={(event) => setChangeReason(event.target.value)}
            placeholder={t('softwareSettings.changeReasonPlaceholder')}
          />
        </div>

        <div className="flex flex-wrap gap-3">
          <Button variant="outline" onClick={handleValidate} disabled={validate.isPending}>
            {t('softwareSettings.validate')}
          </Button>
          <Button onClick={handleSave} disabled={update.isPending || changeReason.trim() === ''}>
            {t('softwareSettings.save')}
          </Button>
        </div>
      </Card>

      <Card>
        <h2 className="p-4 pb-2 text-lg font-semibold">{t('softwareSettings.history')}</h2>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('softwareSettings.revision')}</TableHead>
              <TableHead>{t('softwareSettings.changedBy')}</TableHead>
              <TableHead>{t('softwareSettings.changeReason')}</TableHead>
              <TableHead>{t('softwareSettings.changedAt')}</TableHead>
              <TableHead className="text-right">{t('softwareSettings.actions')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {(history.data ?? []).map((revision) => (
              <TableRow key={revision.revision}>
                <TableCell className="font-mono text-sm">#{revision.revision}</TableCell>
                <TableCell className="text-sm">{revision.changedBy}</TableCell>
                <TableCell className="max-w-64 truncate text-sm" title={revision.changeReason}>
                  {revision.changeReason}
                </TableCell>
                <TableCell className="text-sm">
                  {formatLocalDateTime(revision.createdAt, i18n.language)}
                </TableCell>
                <TableCell className="text-right">
                  {revision.revision !== settings.data.revision ? (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setRollbackTarget(revision.revision)}
                    >
                      {t('softwareSettings.rollback')}
                    </Button>
                  ) : (
                    <span className="text-xs text-muted-foreground">
                      {t('softwareSettings.currentRevision')}
                    </span>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>

      <Dialog open={rollbackTarget != null} onOpenChange={(open) => { if (!open) setRollbackTarget(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t('softwareSettings.rollbackTitle', { revision: rollbackTarget ?? 0 })}
            </DialogTitle>
            <DialogDescription>{t('softwareSettings.rollbackDescription')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="software-rollback-reason">{t('softwareSettings.changeReason')}</Label>
            <Input
              id="software-rollback-reason"
              value={rollbackReason}
              onChange={(event) => setRollbackReason(event.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRollbackTarget(null)}>
              {t('common.cancel')}
            </Button>
            <Button
              onClick={handleRollback}
              disabled={rollback.isPending || rollbackReason.trim() === ''}
            >
              {t('softwareSettings.rollback')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

export default SoftwareSettingsPage
