import { useTranslation } from 'react-i18next'
import { withBasePath } from '@/shared/lib/base-path'
import { PluginNpmReleasePanel } from '@/features/plugin/plugin-npm-publication'
import { PluginGitEvidencePanel } from '@/features/plugin/plugin-git-publication'
import { useAuth } from '@/features/auth/use-auth'
import type { PublicationTask, PublicationResult } from './publication-api'

export function publicationDetail(result: PublicationResult) {
  if (result.type === 'SOFTWARE') return result.status === 'PUBLISHED' ? `/software/apps/${encodeURIComponent(result.slug ?? '')}`
    : `/admin/software?appId=${result.resourceId}&slug=${encodeURIComponent(result.slug ?? '')}`
  if (result.type === 'PLUGIN') return `/dashboard/plugins?projectId=${result.resourceId}`
  return `/space/${encodeURIComponent(result.namespace ?? '')}/${encodeURIComponent(result.slug ?? '')}`
}
export function PublicationResults({ task }: { task: PublicationTask }) {
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const releases = task.progress.results.filter((row, index, rows) => row.type === 'PLUGIN' && row.releaseId
    && rows.findIndex(other => other.releaseId === row.releaseId) === index)
  return <div className="space-y-5">
    {task.progress.results.length > 0 && <ul className="divide-y border-y">
      {task.progress.results.map(row => <li key={row.candidateId} className="flex flex-wrap items-center justify-between gap-3 py-4">
        <div className="min-w-0"><p className="break-words font-medium">{row.slug || row.candidateId}{row.version ? ` · ${row.version}` : ''}</p>
          <p className="mt-1 text-sm text-muted-foreground">{t(`publication.states.${row.status}`, { defaultValue: row.status })}</p>
          {row.error && <p role="alert" className="mt-1 text-sm text-destructive">{t(`pluginImport.status.${row.error}`, { defaultValue: row.error })}</p>}
        </div>
        {row.resourceId && <a className="text-sm font-medium text-primary underline-offset-4 hover:underline" href={withBasePath(publicationDetail(row))}>{t('publication.viewContent')}</a>}
      </li>)}
    </ul>}
    {releases.map(row => <details key={row.releaseId} className="border-b pb-4">
      <summary className="cursor-pointer text-sm font-medium">{t('publication.channels')} · {row.slug}</summary>
      <div className="mt-4 space-y-4">
        <PluginNpmReleasePanel releaseId={row.releaseId!} canManage canReview={hasRole('PLUGIN_ADMIN') || hasRole('SUPER_ADMIN')}
          draft={row.status === 'DRAFT'} approved={row.status === 'PUBLISHED'}
          hasPi={task.progress.preview?.candidates.some(candidate => candidate.runtime === 'pi') ?? false} />
        <PluginGitEvidencePanel releaseId={row.releaseId!} approved={row.status === 'PUBLISHED'} completed={row.status === 'PUBLISHED'} />
      </div>
    </details>)}
  </div>
}
