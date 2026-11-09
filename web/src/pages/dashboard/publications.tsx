import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { usePublicationContents, usePublicationTasks, type PublicationContent } from '@/features/publication/publication-api'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { Pagination } from '@/shared/components/pagination'
import { Button, buttonVariants } from '@/shared/ui/button'
import { withBasePath } from '@/shared/lib/base-path'

function managementPath(item: PublicationContent) {
  if (item.type === 'PLUGIN') return `/dashboard/plugins?projectId=${item.resourceId}`
  if (item.type === 'SOFTWARE') return `/admin/software?appId=${item.resourceId}&slug=${encodeURIComponent(item.slug ?? '')}`
  return `/dashboard/skills?namespace=${encodeURIComponent(item.namespace ?? '')}&q=${encodeURIComponent(item.slug ?? '')}`
}
export function PublicationsPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<'content' | 'tasks'>('content')
  const [type, setType] = useState('ALL')
  const [page, setPage] = useState(0)
  const content = usePublicationContents(type, page)
  const tasks = usePublicationTasks(page)
  const query = tab === 'content' ? content : tasks
  return <div className="mx-auto max-w-5xl space-y-6">
    <div className="flex flex-wrap items-center justify-between gap-4"><DashboardPageHeader title={t('publication.mine')} />
      <Link to="/dashboard/publish" className={buttonVariants()}>{t('publication.new')}</Link></div>
    <div className="flex flex-wrap items-center justify-between gap-3 border-b pb-3">
      <div className="flex gap-1" role="group" aria-label={t('publication.mine')}>
        {(['content', 'tasks'] as const).map(value => <Button key={value} variant={tab === value ? 'secondary' : 'ghost'} aria-pressed={tab === value}
          onClick={() => { setTab(value); setPage(0) }}>{t(`publication.${value}`)}</Button>)}
      </div>
      {tab === 'content' && <select aria-label={t('publication.type')} value={type} onChange={event => { setType(event.target.value); setPage(0) }}
        className="h-9 rounded-md border bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
        <option value="ALL">{t('publication.allTypes')}</option>{['SKILL', 'PLUGIN', 'SOFTWARE'].map(value => <option key={value} value={value}>{t(`publication.types.${value}`)}</option>)}
      </select>}
    </div>
    {query.isPending && <p role="status" className="py-8 text-sm text-muted-foreground">{t('common.loading')}</p>}
    {query.error && <p role="alert" className="text-sm text-destructive">{query.error.message}</p>}
    {tab === 'content' && !content.isPending && !content.error && <div className="divide-y">
      {content.data?.items.map(item => <article key={`${item.type}:${item.resourceId}`} className="flex flex-wrap items-center justify-between gap-4 py-5">
        <div className="min-w-0"><h2 className="break-words font-semibold">{item.name}</h2>
          <p className="mt-1 break-all text-sm text-muted-foreground">{t(`publication.types.${item.type}`)} · {item.namespace ? `@${item.namespace}/` : ''}{item.slug}{item.version ? ` · ${item.version}` : ''}</p></div>
        <div className="flex items-center gap-4 text-sm">
          <Link className="font-medium text-primary hover:underline" to="/dashboard/publish" search={{ namespace: item.namespace,
            visibility: item.visibility === 'AUTHENTICATED' ? undefined : item.visibility, slug: item.slug,
            name: item.type === 'SOFTWARE' ? item.name : undefined,
            projectId: item.type === 'PLUGIN' ? item.resourceId : undefined,
            appId: item.type === 'SOFTWARE' ? item.resourceId : undefined }}>{t('publication.update')}</Link>
          <a className="text-muted-foreground hover:text-foreground hover:underline" href={withBasePath(managementPath(item))}>{t('publication.manage')}</a>
        </div>
      </article>)}
      {content.data?.items.length === 0 && <p className="py-12 text-center text-sm text-muted-foreground">{t('publication.emptyContent')}</p>}
    </div>}
    {tab === 'tasks' && !tasks.isPending && !tasks.error && <div className="divide-y">
      {tasks.data?.items.map(task => <article key={task.id} className="flex flex-wrap items-center justify-between gap-4 py-5">
        <div className="min-w-0"><h2 className="break-words font-semibold">{task.draft.name || task.fileName || t('publication.new')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t(`publication.states.${task.status}`)}
            {task.progress.results.length > 0 ? ` · ${task.progress.results.map(result => t(`publication.states.${result.status}`, { defaultValue: result.status })).join(' / ')}` : ''}</p>
          {task.progress.error && <p className="mt-1 text-sm text-destructive">{task.progress.error}</p>}</div>
        <Link className="text-sm font-medium text-primary hover:underline" to="/dashboard/publish" search={{ task: task.id }}>
          {t(['COMPLETE', 'CANCELLED'].includes(task.status) ? 'publication.viewContent' : 'publication.continue')}</Link>
      </article>)}
      {tasks.data?.items.length === 0 && <p className="py-12 text-center text-sm text-muted-foreground">{t('publication.emptyTasks')}</p>}
    </div>}
    {(query.data?.total ?? 0) > 20 && <Pagination page={page} totalPages={Math.ceil((query.data?.total ?? 0) / 20)} onPageChange={setPage} />}
  </div>
}
