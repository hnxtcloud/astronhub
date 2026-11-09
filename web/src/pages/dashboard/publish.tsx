import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PublicationEditor } from '@/features/publication/publication-editor'
import { normalizePublishPrefill } from '@/features/publish/publish-prefill'
import type { PublicationDraft } from '@/features/publication/publication-api'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'

export function PublishPage() {
  const { t } = useTranslation()
  const search = useSearch({ from: '/dashboard/publish' })
  const navigate = useNavigate()
  const normalized = normalizePublishPrefill(search)
  const prefill: PublicationDraft = {
    ...(normalized.namespace ? { namespace: normalized.namespace } : {}),
    ...(search.visibility ? { visibility: normalized.visibility } : {}),
    ...(search.slug || normalized.resubmitSkill ? { slug: search.slug || normalized.resubmitSkill } : {}),
    ...(search.appId ? { appId: search.appId, type: 'SOFTWARE', name: search.name } : {}),
    ...(search.projectId ? { projectId: search.projectId } : {}),
  }
  return <div className="mx-auto w-full max-w-3xl space-y-7">
    <DashboardPageHeader title={t('publication.title')} />
    {normalized.resubmitSkill && normalized.resubmitVersion && <p className="rounded-lg border p-4 text-sm text-muted-foreground">
      {t('publish.resubmitNotice.title', { skill: `@${normalized.namespace}/${normalized.resubmitSkill}`, version: normalized.resubmitVersion })}
      {' '}{t('publish.resubmitNotice.description')}
    </p>}
    <PublicationEditor taskId={search.task} prefill={Object.keys(prefill).length ? prefill : undefined}
      onTaskChange={task => { void navigate({ to: '/dashboard/publish', search: { ...search, task }, replace: true }) }} />
  </div>
}
