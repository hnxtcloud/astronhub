import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  normalizeSelectValue,
} from '@/shared/ui/select'
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
import { Pagination } from '@/shared/components/pagination'
import {
  useConfirmInspectionMetadata,
  useRetryInspection,
  useSoftwareInspectionDetail,
  useSoftwareInspections,
} from '@/features/software/use-software-queries'
import type { SoftwareInspectionSummary } from '@/api/types'

const ALL_STATUS = '__all__'
const TASK_STATUSES = [
  'PENDING',
  'LEASED',
  'SUCCEEDED',
  'BASIC_FAILED',
  'METADATA_FAILED',
  'EXHAUSTED',
]

/**
 * Inspection console: triage persisted parse tasks. BASIC_FAILED verdicts can
 * only be re-run after a retry (they block confirmation); METADATA_FAILED can
 * be confirmed away by filling in the raw version fields manually.
 */
export function SoftwareInspectionsPage() {
  const { t, i18n } = useTranslation()
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [detailTaskId, setDetailTaskId] = useState<number | null>(null)
  const [confirmTarget, setConfirmTarget] = useState<SoftwareInspectionSummary | null>(null)

  const { data, isLoading } = useSoftwareInspections({
    status: statusFilter || undefined,
    page,
    size: 20,
  })
  const retry = useRetryInspection()
  const confirmMetadata = useConfirmInspectionMetadata()

  const tasks = data?.items ?? []
  const totalElements = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(totalElements / 20))

  useEffect(() => {
    setPage(0)
  }, [statusFilter])

  const formatDate = (iso: string) => formatLocalDateTime(iso, i18n.language)
  const statusLabel = (status: string) => t(`softwareInspections.status.${status}`, status)

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">{t('softwareInspections.title')}</h1>

      <div className="flex flex-wrap items-center gap-3">
        <Select
          value={statusFilter || ALL_STATUS}
          onValueChange={(value) =>
            setStatusFilter(normalizeSelectValue(value) === ALL_STATUS ? '' : (normalizeSelectValue(value) ?? ''))
          }
        >
          <SelectTrigger className="w-52">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_STATUS}>{t('softwareInspections.allStatuses')}</SelectItem>
            {TASK_STATUSES.map((status) => (
              <SelectItem key={status} value={status}>
                {statusLabel(status)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('softwareInspections.task')}</TableHead>
              <TableHead>{t('softwareInspections.file')}</TableHead>
              <TableHead>{t('softwareInspections.status')}</TableHead>
              <TableHead>{t('softwareInspections.attempts')}</TableHead>
              <TableHead>{t('softwareInspections.updatedAt')}</TableHead>
              <TableHead className="text-right">{t('softwareInspections.actions')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground">
                  {t('common.loading')}
                </TableCell>
              </TableRow>
            ) : tasks.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground">
                  {t('softwareInspections.empty')}
                </TableCell>
              </TableRow>
            ) : (
              tasks.map((task) => (
                <TableRow key={task.taskId}>
                  <TableCell>
                    <div className="font-mono text-sm">#{task.taskId}</div>
                    <div className="text-xs text-muted-foreground">{task.format.toUpperCase()}</div>
                  </TableCell>
                  <TableCell>
                    <div className="max-w-56 truncate font-mono text-sm" title={task.fileName}>
                      {task.fileName}
                    </div>
                    <div className="text-xs text-muted-foreground">{task.originalFullVersion}</div>
                  </TableCell>
                  <TableCell className="text-sm">{statusLabel(task.status)}</TableCell>
                  <TableCell className="text-sm">
                    {task.attemptCount}/{task.maxAttempts}
                  </TableCell>
                  <TableCell className="text-sm">{formatDate(task.updatedAt)}</TableCell>
                  <TableCell className="space-x-2 text-right">
                    <Button size="sm" variant="outline" onClick={() => setDetailTaskId(task.taskId)}>
                      {t('softwareInspections.detail')}
                    </Button>
                    {task.status === 'METADATA_FAILED' ? (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setConfirmTarget(task)}
                      >
                        {t('softwareInspections.confirmMetadata')}
                      </Button>
                    ) : null}
                    {task.status !== 'LEASED' && task.status !== 'SUCCEEDED' ? (
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={retry.isPending}
                        onClick={() => retry.mutate(task.taskId)}
                      >
                        {t('softwareInspections.retry')}
                      </Button>
                    ) : null}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        {totalPages > 1 ? (
          <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
        ) : null}
      </Card>

      <TaskDetailDialog taskId={detailTaskId} onClose={() => setDetailTaskId(null)} formatDate={formatDate} />

      {confirmTarget ? (
        <ConfirmMetadataDialog
          task={confirmTarget}
          pending={confirmMetadata.isPending}
          onClose={() => setConfirmTarget(null)}
          onConfirm={(taskId, rawVersionFields) =>
            confirmMetadata.mutate(
              { taskId, rawVersionFields },
              { onSuccess: () => setConfirmTarget(null) },
            )
          }
        />
      ) : null}
    </div>
  )
}

function TaskDetailDialog(props: {
  taskId: number | null
  onClose: () => void
  formatDate: (iso: string) => string
}) {
  const { t } = useTranslation()
  const detail = useSoftwareInspectionDetail(props.taskId ?? undefined)

  if (props.taskId == null) return null

  const summary = detail.data?.task
  const attempts = detail.data?.attempts ?? []

  return (
    <Dialog open onOpenChange={(open) => { if (!open) props.onClose() }}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>
            {t('softwareInspections.detailTitle', { taskId: props.taskId })}
          </DialogTitle>
          <DialogDescription>
            {summary ? `${summary.fileName} · ${summary.originalFullVersion}` : ''}
          </DialogDescription>
        </DialogHeader>
        {detail.isLoading ? (
          <p className="text-sm text-muted-foreground">{t('common.loading')}</p>
        ) : (
          <div className="space-y-4">
            {summary ? (
              <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
                <dt className="text-muted-foreground">{t('softwareInspections.status')}</dt>
                <dd>{t(`softwareInspections.status.${summary.status}`, summary.status)}</dd>
                <dt className="text-muted-foreground">{t('softwareInspections.attempts')}</dt>
                <dd>
                  {summary.attemptCount}/{summary.maxAttempts} ({t('softwareInspections.manualRetries')}: {summary.manualRetryCount})
                </dd>
                <dt className="text-muted-foreground">{t('softwareInspections.policyRevision')}</dt>
                <dd>{summary.policyRevision}</dd>
                {summary.nextAttemptNotBefore ? (
                  <>
                    <dt className="text-muted-foreground">{t('softwareInspections.nextAttempt')}</dt>
                    <dd>{props.formatDate(summary.nextAttemptNotBefore)}</dd>
                  </>
                ) : null}
                {summary.leaseOwner ? (
                  <>
                    <dt className="text-muted-foreground">{t('softwareInspections.leaseOwner')}</dt>
                    <dd className="font-mono text-xs">{summary.leaseOwner}</dd>
                  </>
                ) : null}
              </dl>
            ) : null}
            <div>
              <h3 className="mb-2 text-sm font-medium">{t('softwareInspections.attemptHistory')}</h3>
              {attempts.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('softwareInspections.noAttempts')}</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>#</TableHead>
                      <TableHead>{t('softwareInspections.trigger')}</TableHead>
                      <TableHead>{t('softwareInspections.outcome')}</TableHead>
                      <TableHead>{t('softwareInspections.errorCode')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {attempts.map((attempt) => (
                      <TableRow key={attempt.attemptNo}>
                        <TableCell>{attempt.attemptNo}</TableCell>
                        <TableCell className="text-sm">{attempt.trigger}</TableCell>
                        <TableCell className="text-sm">{attempt.outcome}</TableCell>
                        <TableCell className="font-mono text-xs">{attempt.errorCode ?? '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </div>
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={props.onClose}>
            {t('dialog.close')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function ConfirmMetadataDialog(props: {
  task: SoftwareInspectionSummary
  pending: boolean
  onClose: () => void
  onConfirm: (taskId: number, rawVersionFields: Record<string, unknown>) => void
}) {
  const { t } = useTranslation()
  const [rawFieldsJson, setRawFieldsJson] = useState('{}')
  const [jsonError, setJsonError] = useState('')

  const parsedOk = (() => {
    try {
      const parsed: unknown = JSON.parse(rawFieldsJson)
      return parsed != null && typeof parsed === 'object' && !Array.isArray(parsed)
    } catch {
      return false
    }
  })()

  const submit = () => {
    if (!parsedOk) {
      setJsonError(t('softwareAdmin.rawFieldsInvalid'))
      return
    }
    props.onConfirm(
      props.task.taskId,
      JSON.parse(rawFieldsJson) as Record<string, unknown>,
    )
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) props.onClose() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('softwareInspections.confirmMetadata')}</DialogTitle>
          <DialogDescription>
            {t('softwareInspections.confirmMetadataDescription', { file: props.task.fileName })}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="software-inspection-raw-fields">
            {t('softwareAdmin.rawVersionFields')}
          </Label>
          <Textarea
            id="software-inspection-raw-fields"
            value={rawFieldsJson}
            onChange={(event) => {
              setRawFieldsJson(event.target.value)
              setJsonError('')
            }}
            rows={6}
            className="font-mono text-xs"
          />
          {jsonError ? <p className="text-sm text-destructive">{jsonError}</p> : null}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={props.onClose}>
            {t('common.cancel')}
          </Button>
          <Button onClick={submit} disabled={props.pending || !parsedOk}>
            {t('softwareInspections.confirmMetadata')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export default SoftwareInspectionsPage
