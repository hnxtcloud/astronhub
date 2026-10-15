import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { fetchJson, getCsrfHeaders } from '@/api/client'
import type { components } from '@/api/generated/schema'
import { Button } from '@/shared/ui/button'
import { Textarea } from '@/shared/ui/textarea'
type Channel = components['schemas']['RecoveryChannelView']
type Preview = components['schemas']['ReplanPreview']
type Request = components['schemas']['ReplanRequest']
type Registry = components['schemas']['RegistryView']
type GitSettings = components['schemas']['GitSettingsView']
const base = '/api/v1/plugin-management'
function post<T>(path: string, body: unknown) {
  return fetchJson<T>(path, {
    method: 'POST',
    headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
    timeoutMs: 300000,
  })
}
export function PluginRecoveryPanel({
  releaseId,
  canReview,
  approved,
}: {
  releaseId: number
  canReview: boolean
  approved: boolean
}) {
  const { t } = useTranslation()
  const channels = useQuery({
    queryKey: ['plugin-recovery', releaseId],
    enabled: approved,
    queryFn: () =>
      fetchJson<Channel[]>(
        `${base}/releases/${releaseId}/publication-recovery`
      ),
    refetchInterval: (q) =>
      q.state.data?.some(
        (c) =>
          c.state === 'RUNNING' ||
          c.operations?.some(
            (o) => o.state === 'QUEUED' || o.state === 'RUNNING'
          )
      )
        ? 5000
        : false,
  })
  if (!approved) return null
  return (
    <details open className="space-y-3 rounded border p-3 text-sm">
      <summary className="cursor-pointer font-medium">
        {t('pluginRecovery.title')}
      </summary>
      <p className="max-w-prose text-muted-foreground">
        {t('pluginRecovery.historyWarning')}
      </p>
      {channels.data?.map((c) => (
        <RecoveryChannel
          key={c.channel}
          channel={c}
          releaseId={releaseId}
          canReview={canReview}
        />
      ))}
      {channels.error && <p role="alert">{channels.error.message}</p>}
    </details>
  )
}
function RecoveryChannel({
  channel,
  releaseId,
  canReview,
}: {
  channel: Channel
  releaseId: number
  canReview: boolean
}) {
  const { t } = useTranslation()
  const cache = useQueryClient()
  const [target, setTarget] = useState('')
  const [reason, setReason] = useState('')
  const [candidate, setCandidate] = useState<Preview | null>(null)
  const canReplace =
    canReview &&
    channel.channel !== 'HOSTED' &&
    (channel.state === 'BLOCKED' || channel.state === 'FAILED') &&
    !channel.sourceDisabled
  const registries = useQuery({
    queryKey: ['plugin-npm', 'registries'],
    enabled: canReplace && channel.channel === 'NPM',
    queryFn: () => fetchJson<Registry[]>(base + '/npm/registries'),
  })
  const git = useQuery({
    queryKey: ['plugin-git', 'settings'],
    enabled: canReplace && channel.channel === 'GIT',
    queryFn: () => fetchJson<GitSettings>(base + '/git/settings'),
  })
  const request: Request = {
    channel: channel.channel ?? '',
    targetRevisionId: Number(target),
    expectedRevision: channel.planRevision ?? 0,
    reason,
  }
  const preview = useMutation({
    mutationFn: () =>
      post<Preview>(
        `${base}/releases/${releaseId}/publication-replans/preview`,
        request
      ),
    onSuccess: setCandidate,
  })
  const apply = useMutation({
    mutationFn: () =>
      post<Channel[]>(`${base}/releases/${releaseId}/publication-replans`, {
        ...request,
        previewDigest: candidate?.digest,
      }),
    onSuccess: () => {
      setCandidate(null)
      void cache.invalidateQueries({ queryKey: ['plugin-recovery', releaseId] })
      void cache.invalidateQueries({
        queryKey: ['plugin-npm', 'channels', releaseId],
      })
      void cache.invalidateQueries({ queryKey: ['plugin-management'] })
    },
  })
  const targets =
    channel.channel === 'NPM'
      ? (registries.data ?? []).map((r) => ({
          id: r.id,
          label: `#${r.id} · ${r.registry}`,
          available: r.credentialAvailable,
        }))
      : (git.data?.targets ?? []).map((r) => ({
          id: r.id,
          label: `#${r.id} · @${r.namespace} · ${r.remote} · ${r.branch}`,
          available: r.credentialAvailable,
        }))
  return (
    <article className="space-y-3 border-t pt-3">
      <p className="font-medium">
        {channel.channel} · {t(`publicationLabels.states.${channel.state}`)} · {t('pluginRecovery.revision')}{' '}
        {channel.planRevision}
      </p>
      {channel.receipt && (
        <pre className="overflow-auto whitespace-pre-wrap text-xs">
          {channel.receipt}
        </pre>
      )}
      {channel.state === 'UNKNOWN' && (
        <p className="text-muted-foreground">{t('pluginRecovery.unknown')}</p>
      )}
      {channel.lastError && (
        <p className="text-destructive">{channel.lastError}</p>
      )}
      <details>
        <summary>{t('pluginRecovery.original')}</summary>
        <pre className="overflow-auto whitespace-pre-wrap text-xs">
          {channel.originalSnapshot}
        </pre>
        {channel.plans?.map((p) => (
          <div key={p.revision} className="my-2">
            <p>
              #{p.revision} · {p.requestedBy} · {p.createdAt} · {p.reason}
            </p>
            <pre className="overflow-auto whitespace-pre-wrap text-xs">
              {p.snapshot}
            </pre>
          </div>
        ))}
      </details>
      <AttemptHistory channel={channel} releaseId={releaseId} />
      <GovernanceControl
        channel={channel}
        releaseId={releaseId}
        canReview={canReview}
      />
      {canReplace && (
        <div className="space-y-3">
          <label className="block space-y-1">
            {t('pluginRecovery.target')}
            <select
              required
              className="h-10 w-full rounded border bg-background px-3"
              value={target}
              onChange={(e) => {
                setTarget(e.target.value)
                setCandidate(null)
              }}
            >
              <option value="">{t('pluginManagement.selectNamespace')}</option>
              {targets.map((v) => (
                <option key={v.id} value={v.id} disabled={!v.available}>
                  {v.label}
                </option>
              ))}
            </select>
          </label>
          <label className="block space-y-1">
            {t('pluginRecovery.reason')}
            <Textarea
              required
              maxLength={10000}
              value={reason}
              onChange={(e) => {
                setReason(e.target.value)
                setCandidate(null)
              }}
            />
          </label>
          <Button
            variant="outline"
            disabled={!target || !reason.trim() || preview.isPending}
            onClick={() => preview.mutate()}
          >
            {t('pluginRecovery.preview')}
          </Button>
          {candidate && (
            <>
              <pre className="overflow-auto whitespace-pre-wrap text-xs">
                {candidate.snapshot}
              </pre>
              <p>{t('pluginRecovery.preserve')}</p>
              <Button disabled={apply.isPending} onClick={() => apply.mutate()}>
                {t('pluginRecovery.confirm')}
              </Button>
            </>
          )}
        </div>
      )}
      {[preview.error, apply.error, registries.error, git.error]
        .filter(Boolean)
        .map((e, i) => (
          <p role="alert" key={i}>
            {e instanceof Error ? e.message : t('pluginNpm.failed')}
          </p>
        ))}
    </article>
  )
}

function AttemptHistory({
  channel,
  releaseId,
}: {
  channel: Channel
  releaseId: number
}) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [page, setPage] = useState(0)
  const history = useQuery({
    queryKey: ['plugin-recovery-attempts', releaseId, channel.channel, page],
    enabled: open,
    queryFn: () =>
      fetchJson<components['schemas']['AttemptPage']>(
        `${base}/releases/${releaseId}/publication-attempts?channel=${channel.channel}&page=${page}`
      ),
  })
  const attempts = history.data?.items ?? (page === 0 ? channel.attempts : [])
  return (
    <details onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary>{t('pluginRecovery.attempts')}</summary>
      {attempts?.map((a) => (
        <div key={a.id} className="my-2 space-y-1 border-t pt-2 text-xs">
          <p>
            #{a.id} · {t(`publicationLabels.actions.${a.action}`)} · {t(`publicationLabels.states.${a.outcome}`)} · {t('pluginRecovery.revision')}{' '}
            {a.planRevision}
            {a.operationId && ` · #${a.operationId}`}
          </p>
          <p>
            {a.startedAt} → {a.finishedAt ?? a.leaseUntil}
          </p>
          <p>
            {t('pluginRecovery.heartbeat')}: {a.heartbeatAt}
          </p>
          {a.errorCode && <p>{a.errorCode}</p>}
          {a.receipt && (
            <pre className="overflow-auto whitespace-pre-wrap">{a.receipt}</pre>
          )}
          {a.lateReceipt && (
            <pre className="overflow-auto whitespace-pre-wrap">
              {t('pluginRecovery.late')}: {a.lateReceipt}
            </pre>
          )}
        </div>
      ))}
      {history.data && <p className="text-xs text-muted-foreground">{t('pluginRecovery.pageInfo', {
        page: (history.data.page ?? page) + 1,
        size: history.data.size,
        total: history.data.total,
      })}</p>}
      <div className="flex gap-2">
        <Button
          variant="outline"
          disabled={page === 0 || history.isFetching}
          onClick={() => setPage(page - 1)}
        >
          {t('pluginRecovery.newer')}
        </Button>
        <Button
          variant="outline"
          disabled={!history.data?.hasMore || history.isFetching}
          onClick={() => setPage(page + 1)}
        >
          {t('pluginRecovery.older')}
        </Button>
      </div>
      {history.error && <p role="alert">{history.error.message}</p>}
    </details>
  )
}

function GovernanceControl({
  channel,
  releaseId,
  canReview,
}: {
  channel: Channel
  releaseId: number
  canReview: boolean
}) {
  const { t } = useTranslation()
  const cache = useQueryClient()
  const [reason, setReason] = useState('')
  const [candidate, setCandidate] = useState<
    components['schemas']['GovernancePreview'] | null
  >(null)
  const invalidate = () => {
    void cache.invalidateQueries({ queryKey: ['plugin-recovery', releaseId] })
    void cache.invalidateQueries({
      queryKey: ['plugin-recovery-attempts', releaseId],
    })
    void cache.invalidateQueries({ queryKey: ['plugin-management'] })
    setCandidate(null)
  }
  const request: components['schemas']['GovernanceRequest'] = {
    channel: channel.channel ?? '',
    action: 'WITHDRAW_CURRENT',
    expectedRevision: channel.planRevision ?? 0,
    reason,
  }
  const preview = useMutation({
    mutationFn: () =>
      post<components['schemas']['GovernancePreview']>(
        `${base}/releases/${releaseId}/channel-governance/preview`,
        request
      ),
    onSuccess: setCandidate,
  })
  const apply = useMutation({
    mutationFn: () =>
      post<Channel[]>(`${base}/releases/${releaseId}/channel-governance`, {
        ...request,
        previewDigest: candidate?.digest,
      }),
    onSuccess: invalidate,
  })
  const retry = useMutation({
    mutationFn: (id: number) =>
      post<Channel[]>(
        `${base}/releases/${releaseId}/channel-governance/${id}/retry`,
        {}
      ),
    onSuccess: invalidate,
  })
  if (channel.channel === 'HOSTED') return null
  return (
    <div className="space-y-2">
      {channel.sourceDisabled && <p>{t('pluginRecovery.disabled')}</p>}
      {channel.operations?.map((op) => (
        <div key={op.id} className="space-y-1 rounded border p-2">
          <p>
            #{op.id} · {t(`publicationLabels.actions.${op.action}`)} · {t(`publicationLabels.states.${op.state}`)}
          </p>
          <p>
            {op.requestedBy} · {op.createdAt} · {op.reason}
          </p>
          {op.state === 'UNSUPPORTED' && (
            <p>{t('pluginRecovery.npmRetained')}</p>
          )}
          {op.receipt && (
            <pre className="overflow-auto whitespace-pre-wrap text-xs">
              {op.receipt}
            </pre>
          )}
          {op.lastError && <p>{op.lastError}</p>}
          {canReview &&
            ['FAILED', 'UNKNOWN', 'QUEUED'].includes(op.state ?? '') && (
              <Button
                variant="outline"
                disabled={retry.isPending}
                onClick={() => retry.mutate(op.id!)}
              >
                {t('pluginRecovery.retryWithdrawal')}
              </Button>
            )}
        </div>
      ))}
      {canReview &&
        channel.state === 'SUCCEEDED' &&
        !channel.sourceDisabled && (
          <details>
            <summary>{t('pluginRecovery.withdraw')}</summary>
            <p className="my-2 max-w-prose">
              {t(
                channel.channel === 'NPM'
                  ? 'pluginRecovery.npmRetained'
                  : 'pluginRecovery.gitRetained'
              )}
            </p>
            <label className="block space-y-1">
              {t('pluginRecovery.withdrawReason')}
              <Textarea
                maxLength={10000}
                value={reason}
                onChange={(e) => {
                  setReason(e.target.value)
                  setCandidate(null)
                }}
              />
            </label>
            <Button
              className="my-2"
              variant="outline"
              disabled={!reason.trim() || preview.isPending}
              onClick={() => preview.mutate()}
            >
              {t('pluginRecovery.previewWithdrawal')}
            </Button>
            {candidate && (
              <div>
                <p>
                  {t(
                    channel.channel === 'NPM'
                      ? 'pluginRecovery.npmRetained'
                      : 'pluginRecovery.gitRetained'
                  )}
                </p>
                <Button
                  disabled={apply.isPending}
                  onClick={() => apply.mutate()}
                >
                  {t('pluginRecovery.confirmWithdrawal')}
                </Button>
              </div>
            )}
          </details>
        )}
      {[preview.error, apply.error, retry.error].filter(Boolean).map((e, i) => (
        <p role="alert" key={i}>
          {e instanceof Error ? e.message : t('pluginNpm.failed')}
        </p>
      ))}
    </div>
  )
}
