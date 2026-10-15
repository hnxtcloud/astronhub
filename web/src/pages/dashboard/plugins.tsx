import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { PluginOfflineImportPanel } from '@/features/plugin/plugin-offline-import'
import { useAuth } from '@/features/auth/use-auth'
import { useCreatePluginProject, usePluginCommand, usePluginNamespaces, usePluginProject, usePluginProjects,
  type PluginCommandBody, type PluginProject, type PluginProjectInput, type PluginRelease } from '@/features/plugin/plugin-api'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import { Pagination } from '@/shared/components/pagination'
import { withBasePath } from '@/shared/lib/base-path'

const selectClass = 'h-10 w-full rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring'

export function PluginManagementPage() {
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const reviewer = hasRole('PLUGIN_ADMIN') || hasRole('SUPER_ADMIN')
  const [selected, setSelected] = useState<number | null>(null)
  const [page, setPage] = useState(0)
  const [pending, setPending] = useState(false)
  const [namespaceId, setNamespaceId] = useState('')
  const [slug, setSlug] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [summary, setSummary] = useState('')
  const [visibility, setVisibility] = useState<PluginProjectInput['visibility']>('PRIVATE')
  const projects = usePluginProjects(page, pending)
  const project = usePluginProject(selected)
  const namespaces = usePluginNamespaces()
  const create = useCreatePluginProject(setSelected)
  function createProject(event: FormEvent) {
    event.preventDefault()
    create.mutate({ namespaceId: Number(namespaceId), slug, displayName, summary, visibility })
  }
  return <div className="mx-auto max-w-7xl space-y-7 p-4 sm:p-6">
    <header><h1 className="text-2xl font-semibold">{t('pluginManagement.title')}</h1>
      <p className="mt-2 max-w-prose text-sm text-muted-foreground">{t('pluginManagement.description')}</p></header>
    <PluginOfflineImportPanel reviewer={reviewer} namespaces={namespaces.data ?? []} projects={projects.data?.items ?? []} />
    <details open={selected === null} className="rounded-lg border p-4">
      <summary className="cursor-pointer font-medium">{t('pluginManagement.createProject')}</summary>
      <form onSubmit={createProject} className="mt-4 grid gap-4 sm:grid-cols-2">
        <label className="space-y-1 text-sm">{t('pluginManagement.namespace')}<select required className={selectClass} value={namespaceId} onChange={e => setNamespaceId(e.target.value)}>
          <option value="">{t('pluginManagement.selectNamespace')}</option>
          {(namespaces.data ?? []).filter(n => n.status === 'ACTIVE').map(n => <option key={n.id} value={n.id}>{n.displayName}</option>)}
        </select></label>
        <label className="space-y-1 text-sm">{t('pluginManagement.slug')}<Input required maxLength={64} value={slug} onChange={e => setSlug(e.target.value)} /></label>
        <label className="space-y-1 text-sm">{t('pluginManagement.displayName')}<Input required maxLength={200} value={displayName} onChange={e => setDisplayName(e.target.value)} /></label>
        <label className="space-y-1 text-sm">{t('pluginManagement.visibility')}<select className={selectClass} value={visibility} onChange={e => {
          const value = e.target.value
          if (value === 'PRIVATE' || value === 'NAMESPACE_ONLY' || value === 'PUBLIC') setVisibility(value)
        }}>
          {['PRIVATE', 'NAMESPACE_ONLY', 'PUBLIC'].map(value => <option key={value} value={value}>{t(`pluginManagement.visibilityValues.${value}`)}</option>)}
        </select></label>
        <label className="space-y-1 text-sm sm:col-span-2">{t('pluginManagement.summary')}<Textarea maxLength={30000} value={summary} onChange={e => setSummary(e.target.value)} /></label>
        <div><Button type="submit" disabled={create.isPending || !namespaceId}>{t('pluginManagement.createProject')}</Button></div>
        {create.error && <p role="alert" className="text-sm text-destructive">{create.error.message}</p>}
        {namespaces.error && <p role="alert" className="text-sm text-destructive">{namespaces.error.message}</p>}
      </form>
    </details>
    <div className="grid gap-6 lg:grid-cols-[18rem_minmax(0,1fr)]">
      <aside className="space-y-4">
        {reviewer && <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={pending} onChange={e => { setPending(e.target.checked); setPage(0) }} />{t('pluginManagement.pendingOnly')}</label>}
        {projects.isLoading && <p aria-live="polite">{t('common.loading')}</p>}
        {projects.error && <p role="alert" className="text-destructive">{projects.error.message}</p>}
        <ul className="divide-y rounded-lg border">{projects.data?.items.map(item => <li key={item.id}>
          <button type="button" aria-current={selected === item.id ? 'true' : undefined} onClick={() => setSelected(item.id)}
            className="w-full px-4 py-3 text-left hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring aria-[current=true]:bg-accent">
            <span className="block font-medium">{item.displayName}</span><span className="block break-all text-xs text-muted-foreground">@{item.namespace}/{item.slug}</span>
          </button></li>)}</ul>
        {projects.data?.items.length === 0 && <p className="text-sm text-muted-foreground">{t('pluginManagement.empty')}</p>}
        <Pagination page={page} totalPages={Math.max(1, Math.ceil((projects.data?.total ?? 0) / 20))} onPageChange={setPage} />
      </aside>
      <div className="min-w-0 space-y-5">
        {project.isLoading && selected !== null && <p aria-live="polite">{t('common.loading')}</p>}
        {project.error && <p role="alert" className="text-destructive">{project.error.message}</p>}
        {project.data ? <PluginProjectEditor key={project.data.id} project={project.data} direct={hasRole('SUPER_ADMIN')} />
          : selected === null && <p className="py-6 text-sm text-muted-foreground">{t('pluginManagement.selectProject')}</p>}
      </div>
    </div>
  </div>
}

function PluginProjectEditor({ project, direct }: { project: PluginProject; direct: boolean }) {
  const { t } = useTranslation()
  const command = usePluginCommand()
  const [name, setName] = useState(project.displayName)
  const [summary, setSummary] = useState(project.summary ?? '')
  const [version, setVersion] = useState('')
  const [changelog, setChangelog] = useState('')
  return <>
    <header><h2 className="text-xl font-semibold">{project.displayName}</h2><p className="text-sm text-muted-foreground">@{project.namespace}/{project.slug} · {t(`pluginManagement.visibilityValues.${project.visibility}`)}</p></header>
    {project.canManage && <>
      <form className="space-y-3" onSubmit={e => { e.preventDefault(); command.mutate({ path: `/projects/${project.id}`, method: 'PATCH', body: { displayName: name, summary } }) }}>
        <label className="block space-y-1 text-sm">{t('pluginManagement.editName')}<Input required maxLength={200} value={name} onChange={e => setName(e.target.value)} /></label>
        <label className="block space-y-1 text-sm">{t('pluginManagement.summary')}<Textarea maxLength={30000} value={summary} onChange={e => setSummary(e.target.value)} /></label>
        <Button type="submit" variant="outline" disabled={command.isPending}>{t('pluginManagement.saveMetadata')}</Button>
      </form>
      <form className="space-y-3 border-t pt-5" onSubmit={e => { e.preventDefault(); command.mutate({ path: `/projects/${project.id}/releases`, body: { version, changelog } }) }}>
        <h3 className="font-medium">{t('pluginManagement.newVersion')}</h3>
        <label className="block space-y-1 text-sm">{t('pluginManagement.version')}<Input required maxLength={64} placeholder="1.0.0" value={version} onChange={e => setVersion(e.target.value)} /></label>
        <label className="block space-y-1 text-sm">{t('pluginManagement.changelog')}<Textarea maxLength={30000} value={changelog} onChange={e => setChangelog(e.target.value)} /></label>
        <Button type="submit" disabled={command.isPending}>{t('pluginManagement.createDraft')}</Button>
      </form>
    </>}
    {command.error && <p role="alert" className="text-sm text-destructive">{command.error.message}</p>}
    <section className="space-y-5 border-t pt-5"><h3 className="font-medium">{t('pluginManagement.versions')}</h3>
      {project.releases.length === 0 && <p className="text-sm text-muted-foreground">{t('pluginManagement.noVersions')}</p>}
      {project.releases.map(release => <PluginReleasePanel key={release.id} release={release} canManage={project.canManage} canReview={project.canReview} direct={direct} />)}
    </section>
  </>
}

function PluginReleasePanel({ release, canManage, canReview, direct }: { release: PluginRelease; canManage: boolean; canReview: boolean; direct: boolean }) {
  const { t } = useTranslation()
  const command = usePluginCommand()
  const [archive, setArchive] = useState<File | null>(null)
  const [notes, setNotes] = useState('')
  const [testResult, setTestResult] = useState('UNKNOWN')
  const [environment, setEnvironment] = useState('')
  const [changelog, setChangelog] = useState(release.changelog ?? '')
  const draft = release.status === 'DRAFT'
  const reviewable = release.status === 'PENDING_REVIEW' && !release.publication
  const reviewBody = { notes, runtimeTestResult: testResult, testEnvironment: environment || undefined }
  const action = (name: string, body?: PluginCommandBody) => command.mutate({ path: `/releases/${release.id}/${name}`, body })
  return <article className="space-y-4 rounded-lg border p-4">
    <div className="flex flex-wrap items-baseline justify-between gap-2"><h4 className="font-semibold">{release.version}</h4><span className="text-sm">{t(`pluginManagement.status.${release.status}`)}</span></div>
    {release.publication && <p className="text-sm" role="status">{t(`pluginManagement.publication.${release.publication.status}`)}
      <span className="ml-2 text-muted-foreground">{release.publication.completedChannels || '0'} / {release.publication.requiredChannels}</span></p>}
    {release.publication?.lastError && <p className="text-sm text-destructive">{t('pluginManagement.hostedFailed')}</p>}
    {release.changelog && !draft && <p className="whitespace-pre-wrap text-sm">{release.changelog}</p>}
    {draft && canManage && <>
      <label className="block space-y-1 text-sm">{t('pluginManagement.changelog')}<Textarea maxLength={30000} value={changelog} onChange={e => setChangelog(e.target.value)} /></label>
      <Button variant="outline" disabled={command.isPending} onClick={() => command.mutate({ path: `/releases/${release.id}`, method: 'PATCH', body: { changelog } })}>{t('pluginManagement.saveDraft')}</Button>
      {release.distributions.length === 0 && <form className="space-y-2" onSubmit={e => { e.preventDefault(); if (archive) command.mutate({ path: `/releases/${release.id}/archive`, archive }) }}>
        <label className="block space-y-1 text-sm">{t('pluginManagement.archive')}<Input type="file" accept=".zip,application/zip" required onChange={e => setArchive(e.target.files?.[0] ?? null)} /></label>
        <p className="max-w-prose text-xs text-muted-foreground">{t('pluginManagement.archiveHelp')}</p>
        <Button type="submit" disabled={!archive || command.isPending}>{t('pluginManagement.importArchive')}</Button>
      </form>}
    </>}
    {release.distributions.map(distribution => <div key={distribution.id} className="space-y-2 text-sm">
      <p className="font-medium">{distribution.runtimeKey}: {distribution.nativeName}@{distribution.nativeVersion}</p>
      <p>{t(`pluginManagement.evidence.${distribution.compatibilityEvidence}`)} · {t(`pluginManagement.evidence.${distribution.securityAssessment}`)}</p>
      <p className="break-all font-mono text-xs">SHA256 {distribution.sha256}</p>
      <details><summary className="cursor-pointer">{t('pluginManagement.inspectEvidence')}</summary>
        <pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap break-all rounded bg-muted p-3 text-xs">{distribution.manifestJson}{'\n'}{distribution.inventoryJson}</pre></details>
      <a className="inline-block underline underline-offset-4" href={withBasePath(`/api/v1/plugin-management/releases/${release.id}/${release.status === 'PUBLISHED' ? 'download' : 'archive'}?runtime=${encodeURIComponent(distribution.runtimeKey)}`)}>{t('pluginManagement.download')}</a>
    </div>)}
    {draft && canManage && <Button disabled={command.isPending || release.distributions.length === 0} onClick={() => action('submit')}>{t('pluginManagement.submit')}</Button>}
    {canReview && (reviewable || (draft && direct)) && <form className="space-y-3 border-t pt-4" onSubmit={e => { e.preventDefault(); action(draft ? 'direct-publish' : 'approve', reviewBody) }}>
      <label className="block space-y-1 text-sm">{t('pluginManagement.reviewNotes')}<Textarea required maxLength={10000} value={notes} onChange={e => setNotes(e.target.value)} /></label>
      <label className="block space-y-1 text-sm">{t('pluginManagement.runtimeTest')}<select className={selectClass} value={testResult} onChange={e => setTestResult(e.target.value)}>
        {['UNKNOWN', 'PASSED', 'FAILED'].map(value => <option key={value} value={value}>{t(`pluginManagement.testResult.${value}`)}</option>)}
      </select></label>
      {testResult !== 'UNKNOWN' && <label className="block space-y-1 text-sm">{t('pluginManagement.testEnvironment')}<Input required maxLength={10000} value={environment} onChange={e => setEnvironment(e.target.value)} /></label>}
      <div className="flex flex-wrap gap-2"><Button type="submit" disabled={command.isPending || release.distributions.length === 0}>{t(draft ? 'pluginManagement.directApprove' : 'pluginManagement.approve')}</Button>
        {reviewable && <Button type="button" variant="destructive" disabled={command.isPending || !notes.trim() || (testResult !== 'UNKNOWN' && !environment.trim())} onClick={() => action('reject', reviewBody)}>{t('pluginManagement.reject')}</Button>}</div>
    </form>}
    {canReview && release.publication && release.publication.status !== 'SUCCEEDED' && !release.publication.completedChannels.includes('HOSTED') && <Button variant="outline" disabled={command.isPending} onClick={() => action('publish-hosted')}>{t('pluginManagement.retryHosted')}</Button>}
    {release.reviews.map((review, index) => <p key={index} className="whitespace-pre-wrap text-sm text-muted-foreground">{review.reviewerId}: {review.notes} · {t(`pluginManagement.testResult.${review.runtimeTestResult}`)}{review.testEnvironment ? ` (${review.testEnvironment})` : ''}</p>)}
    {command.isPending && <p role="status" className="text-sm">{t('common.loading')}</p>}
    {command.error && <p role="alert" className="text-sm text-destructive">{command.error.message}</p>}
  </article>
}
