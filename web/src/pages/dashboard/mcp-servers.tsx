import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { buildMcpClientConfiguration } from '@/features/mcp/connection-configuration'
import { useAuth } from '@/features/auth/use-auth'
import { useCreateMcpEntry, useMcpCommand, useMcpEntries, useMcpEntry, useMcpNamespaces,
  type McpEntry, type McpEntryInput, type McpProfile } from '@/features/mcp/mcp-management-api'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import { Pagination } from '@/shared/components/pagination'
import { withBasePath } from '@/shared/lib/base-path'

const selectClass = 'h-10 w-full rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring'
const transports = ['STDIO', 'STREAMABLE_HTTP', 'LEGACY_HTTP_SSE', 'CUSTOM'] as const
type TransportDraft = (typeof transports)[number]

interface VariableDraft {
  name: string
  description: string
  required: boolean
  secret: boolean
  defaultValue: string
}

interface ProfileDraft {
  profileKey: string
  displayName: string
  transportType: TransportDraft
  endpointTemplate: string
  commandTemplate: string
  arguments: string
  variables: VariableDraft[]
}

const emptyProfile = (): ProfileDraft => ({
  profileKey: '', displayName: '', transportType: 'STDIO',
  endpointTemplate: '', commandTemplate: '', arguments: '', variables: [],
})

function splitArguments(value: string): string[] {
  return value.split('\n').map(line => line.trim()).filter(Boolean)
}

type McpProfileInput = NonNullable<McpEntryInput['profiles']>[number]

function toInput(draft: ProfileDraft): McpProfileInput {
  const variables = draft.variables
    .filter(variable => variable.name.trim())
    .map(variable => ({
      name: variable.name.trim(), description: variable.description || undefined,
      required: variable.required, secret: variable.secret,
      defaultValue: variable.secret || !variable.defaultValue ? undefined : variable.defaultValue,
    }))
  return {
    profileKey: draft.profileKey.trim(), displayName: draft.displayName.trim(),
    transportType: draft.transportType,
    endpointTemplate: draft.endpointTemplate || undefined,
    commandTemplate: draft.commandTemplate || undefined,
    arguments: splitArguments(draft.arguments),
    variables,
  }
}

function draftFromProfile(profile: McpProfile): ProfileDraft {
  return {
    profileKey: profile.profileKey, displayName: profile.displayName,
    transportType: profile.transportType,
    endpointTemplate: profile.endpointTemplate ?? '', commandTemplate: profile.commandTemplate ?? '',
    arguments: (profile.arguments ?? []).join('\n'),
    variables: profile.variables.map(variable => ({
      name: variable.name, description: variable.description ?? '', required: variable.required,
      secret: variable.secret, defaultValue: variable.defaultValue ?? '',
    })),
  }
}

function previewConfiguration(draft: ProfileDraft, slug: string): string | null {
  return buildMcpClientConfiguration({
    profileKey: draft.profileKey, displayName: draft.displayName, transportType: draft.transportType,
    endpointTemplate: draft.endpointTemplate || undefined,
    commandTemplate: draft.commandTemplate || undefined,
    arguments: splitArguments(draft.arguments),
    variables: draft.variables.filter(v => v.name.trim()).map(v => ({
      name: v.name.trim(), secret: v.secret, defaultValue: v.secret || !v.defaultValue ? undefined : v.defaultValue,
    })),
    configurationAvailable: true,
  }, slug)
}

export function McpManagementPage() {
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const reviewer = hasRole('MCP_ADMIN') || hasRole('SUPER_ADMIN')
  const [selected, setSelected] = useState<number | null>(() => {
    const value = typeof window === 'undefined' ? 0 : Number(new URLSearchParams(window.location.search).get('entryId'))
    return Number.isSafeInteger(value) && value > 0 ? value : null
  })
  const [page, setPage] = useState(0)
  const [pending, setPending] = useState(false)
  const [namespaceId, setNamespaceId] = useState('')
  const [slug, setSlug] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [summary, setSummary] = useState('')
  const [visibility, setVisibility] = useState<NonNullable<McpEntryInput['visibility']>>('PRIVATE')
  const [serverJson, setServerJson] = useState('')
  const entries = useMcpEntries(page, pending)
  const entry = useMcpEntry(selected)
  const namespaces = useMcpNamespaces()
  const create = useCreateMcpEntry(setSelected)
  function createEntry(event: FormEvent) {
    event.preventDefault()
    create.mutate({ namespaceId: Number(namespaceId), slug, displayName, summary: summary || undefined, visibility,
      serverJson: serverJson || undefined })
  }
  return <div className="mx-auto max-w-7xl space-y-7 p-4 sm:p-6">
    <header><h1 className="text-2xl font-semibold">{t('mcpManagement.title')}</h1>
      <p className="mt-2 text-sm text-muted-foreground"><a className="text-primary hover:underline" href={withBasePath('/mcp-servers')}>{t('mcpManagement.catalog')}</a></p></header>
    <details className="rounded-lg border p-4">
      <summary className="cursor-pointer font-medium">{t('mcpManagement.createEntry')}</summary>
      <form onSubmit={createEntry} className="mt-4 grid gap-4 sm:grid-cols-2">
        <label className="space-y-1 text-sm">{t('mcpManagement.namespace')}<select required className={selectClass} value={namespaceId} onChange={e => setNamespaceId(e.target.value)}>
          <option value="">{t('mcpManagement.selectNamespace')}</option>
          {(namespaces.data ?? []).filter(n => n.status === 'ACTIVE').map(n => <option key={n.id} value={n.id}>{n.displayName}</option>)}
        </select></label>
        <label className="space-y-1 text-sm">{t('mcpManagement.slug')}<Input required maxLength={64} value={slug} onChange={e => setSlug(e.target.value)} /></label>
        <label className="space-y-1 text-sm">{t('mcpManagement.displayName')}<Input required maxLength={200} value={displayName} onChange={e => setDisplayName(e.target.value)} /></label>
        <label className="space-y-1 text-sm">{t('mcpManagement.visibility')}<select className={selectClass} value={visibility} onChange={e => {
          const value = e.target.value
          if (value === 'PRIVATE' || value === 'NAMESPACE_ONLY' || value === 'PUBLIC') setVisibility(value)
        }}>
          {['PRIVATE', 'NAMESPACE_ONLY', 'PUBLIC'].map(value => <option key={value} value={value}>{t(`mcpManagement.visibilityValues.${value}`)}</option>)}
        </select></label>
        <label className="space-y-1 text-sm sm:col-span-2">{t('mcpManagement.summary')}<Textarea maxLength={30000} value={summary} onChange={e => setSummary(e.target.value)} /></label>
        <div className="space-y-2 sm:col-span-2">
          <label className="block space-y-1 text-sm">{t('mcpManagement.serverJson')}<Textarea className="font-mono" maxLength={200000} spellCheck={false} value={serverJson} onChange={e => setServerJson(e.target.value)} /></label>
          <label className="inline-block cursor-pointer text-sm text-primary underline underline-offset-4">
            {t('mcpManagement.uploadServerJson')}
            <input type="file" accept=".json,application/json" className="hidden" onChange={e => {
              const file = e.target.files?.[0]
              if (file) void file.text().then(setServerJson)
            }} />
          </label>
          <p className="max-w-prose text-xs text-muted-foreground">{t('mcpManagement.serverJsonHelp')}</p>
        </div>
        <div><Button type="submit" disabled={create.isPending || !namespaceId}>{t('mcpManagement.createEntry')}</Button></div>
        {create.error && <p role="alert" className="text-sm text-destructive">{create.error.message}</p>}
        {namespaces.error && <p role="alert" className="text-sm text-destructive">{namespaces.error.message}</p>}
      </form>
    </details>
    <div className="grid gap-6 lg:grid-cols-[18rem_minmax(0,1fr)]">
      <aside className="space-y-4">
        {reviewer && <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={pending} onChange={e => { setPending(e.target.checked); setPage(0) }} />{t('mcpManagement.pendingOnly')}</label>}
        {entries.isLoading && <p aria-live="polite">{t('common.loading')}</p>}
        {entries.error && <p role="alert" className="text-destructive">{entries.error.message}</p>}
        <ul className="divide-y rounded-lg border">{entries.data?.items.map(item => <li key={item.id}>
          <button type="button" aria-current={selected === item.id ? 'true' : undefined} onClick={() => setSelected(item.id)}
            className="w-full px-4 py-3 text-left hover:bg-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring aria-[current=true]:bg-secondary">
            <span className="block font-medium">{item.displayName}</span><span className="block break-all text-xs text-muted-foreground">@{item.namespace}/{item.slug}</span>
            {item.hidden && <span className="block text-xs text-destructive">{t('mcpManagement.hidden')}</span>}
          </button></li>)}</ul>
        {entries.data?.items.length === 0 && <p className="text-sm text-muted-foreground">{t('mcpManagement.empty')}</p>}
        {(entries.data?.total ?? 0) > 20 && <Pagination page={page} totalPages={Math.ceil((entries.data?.total ?? 0) / 20)} onPageChange={setPage} />}
      </aside>
      <div className="min-w-0 space-y-5">
        {entry.isLoading && selected !== null && <p aria-live="polite">{t('common.loading')}</p>}
        {entry.error && <p role="alert" className="text-destructive">{entry.error.message}</p>}
        {entry.data ? <McpEntryEditor key={entry.data.id} entry={entry.data} direct={hasRole('SUPER_ADMIN')} />
          : selected === null && <p className="py-6 text-sm text-muted-foreground">{t('mcpManagement.selectEntry')}</p>}
      </div>
    </div>
  </div>
}

function McpEntryEditor({ entry, direct }: { entry: McpEntry; direct: boolean }) {
  const { t } = useTranslation()
  const command = useMcpCommand()
  const [name, setName] = useState(entry.displayName)
  const [summary, setSummary] = useState(entry.summary ?? '')
  const [description, setDescription] = useState('')
  const [localCuration, setLocalCuration] = useState('')
  const [revisionServerJson, setRevisionServerJson] = useState('')
  const liveDraft = entry.revisions.find(revision => revision.status === 'DRAFT' || revision.status === 'PENDING_REVIEW')
  const canStartDraft = entry.canManage && !liveDraft
  const [drafts, setDrafts] = useState<ProfileDraft[]>([])
  function createRevision(event: FormEvent) {
    event.preventDefault()
    command.mutate({ path: `/entries/${entry.id}/revisions`, body: {
      description: description || undefined, localCuration: localCuration || undefined,
      serverJson: revisionServerJson || undefined,
      profiles: drafts.length > 0 ? drafts.map(toInput) : undefined,
    } })
  }
  return <>
    <header><h2 className="text-xl font-semibold">{entry.displayName}</h2>
      <p className="text-sm text-muted-foreground">@{entry.namespace}/{entry.slug} · {t(`mcpManagement.visibilityValues.${entry.visibility}`)}
        {entry.hidden && <> · {t('mcpManagement.hidden')}</>}</p></header>
    {entry.canGovern && <McpGovernancePanel entryId={entry.id} />}
    {entry.canManage && <>
      <details><summary className="cursor-pointer text-sm font-medium">{t('mcpManagement.editEntry')}</summary>
        <form className="mt-3 space-y-3" onSubmit={e => { e.preventDefault(); command.mutate({ path: `/entries/${entry.id}`, method: 'PATCH', body: { displayName: name, summary } }) }}>
          <label className="block space-y-1 text-sm">{t('mcpManagement.displayName')}<Input required maxLength={200} value={name} onChange={e => setName(e.target.value)} /></label>
          <label className="block space-y-1 text-sm">{t('mcpManagement.summary')}<Textarea maxLength={30000} value={summary} onChange={e => setSummary(e.target.value)} /></label>
          <Button type="submit" variant="outline" disabled={command.isPending}>{t('mcpManagement.saveMetadata')}</Button>
        </form>
      </details>
      {canStartDraft && <details><summary className="cursor-pointer text-sm font-medium">{t('mcpManagement.newDraft')}</summary>
        <form className="mt-3 space-y-3 border-t pt-5" onSubmit={createRevision}>
          <label className="block space-y-1 text-sm">{t('mcpManagement.description')}<Textarea maxLength={30000} value={description} onChange={e => setDescription(e.target.value)} /></label>
          <McpProfilesEditor drafts={drafts} setDrafts={setDrafts} slug={entry.slug} />
          <div className="space-y-2">
            <label className="block space-y-1 text-sm">{t('mcpManagement.serverJson')}<Textarea className="font-mono" maxLength={200000} spellCheck={false} value={revisionServerJson} onChange={e => setRevisionServerJson(e.target.value)} /></label>
            <p className="max-w-prose text-xs text-muted-foreground">{t('mcpManagement.serverJsonHelp')}</p>
          </div>
          <label className="block space-y-1 text-sm">{t('mcpManagement.localCuration')}<Textarea maxLength={30000} value={localCuration} onChange={e => setLocalCuration(e.target.value)} /></label>
          <Button type="submit" disabled={command.isPending || (drafts.length === 0 && !revisionServerJson)}>{t('mcpManagement.createDraft')}</Button>
        </form>
      </details>}
    </>}
    {command.error && <p role="alert" className="text-sm text-destructive">{command.error.message}</p>}
    <section className="space-y-5 border-t pt-5"><h3 className="font-medium">{t('mcpManagement.revisions')}</h3>
      {entry.revisions.length === 0 && <p className="text-sm text-muted-foreground">{t('mcpManagement.noRevisions')}</p>}
      {entry.revisions.map(revision => <McpRevisionPanel key={revision.id} entry={entry} revision={revision} direct={direct} />)}
    </section>
  </>
}

function McpProfilesEditor({ drafts, setDrafts, slug }: { drafts: ProfileDraft[]; setDrafts: (drafts: ProfileDraft[]) => void; slug: string }) {
  const { t } = useTranslation()
  const update = (index: number, patch: Partial<ProfileDraft>) =>
    setDrafts(drafts.map((draft, i) => i === index ? { ...draft, ...patch } : draft))
  return <div className="space-y-4">
    <p className="font-medium">{t('mcpManagement.profiles')}</p>
    {drafts.map((draft, index) => <div key={index} className="space-y-3 rounded-lg border p-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="space-y-1 text-sm">{t('mcpManagement.profileKey')}<Input required maxLength={100} pattern="[A-Za-z0-9][A-Za-z0-9._-]*" value={draft.profileKey} onChange={e => update(index, { profileKey: e.target.value })} /></label>
        <label className="space-y-1 text-sm">{t('mcpManagement.profileDisplayName')}<Input required maxLength={200} value={draft.displayName} onChange={e => update(index, { displayName: e.target.value })} /></label>
        <label className="space-y-1 text-sm">{t('mcpManagement.transport')}<select className={selectClass} value={draft.transportType} onChange={e => update(index, { transportType: e.target.value as TransportDraft })}>
          {transports.map(value => <option key={value} value={value}>{t(`mcpDetail.transports.${value}`)}</option>)}
        </select></label>
      </div>
      {draft.transportType === 'STDIO'
        ? <>
          <label className="block space-y-1 text-sm">{t('mcpManagement.commandTemplate')}<Input required maxLength={2000} value={draft.commandTemplate} onChange={e => update(index, { commandTemplate: e.target.value })} /></label>
          <label className="block space-y-1 text-sm">{t('mcpManagement.arguments')}<Textarea className="font-mono" maxLength={2000} rows={3} spellCheck={false} value={draft.arguments} onChange={e => update(index, { arguments: e.target.value })} /></label>
        </>
        : <label className="block space-y-1 text-sm">{t('mcpManagement.endpointTemplate')}<Input maxLength={2000} value={draft.endpointTemplate} onChange={e => update(index, { endpointTemplate: e.target.value })} /></label>}
      <div className="space-y-2">
        <p className="text-sm font-medium">{t('mcpManagement.variables')}</p>
        {draft.variables.map((variable, variableIndex) => <div key={variableIndex} className="grid items-end gap-2 sm:grid-cols-[10rem_10rem_6rem_auto]">
          <Input required maxLength={128} placeholder={t('mcpManagement.variableName')} value={variable.name}
            onChange={e => update(index, { variables: draft.variables.map((v, i) => i === variableIndex ? { ...v, name: e.target.value } : v) })} />
          <Input maxLength={1000} placeholder={t('mcpManagement.variableDescription')} value={variable.description}
            onChange={e => update(index, { variables: draft.variables.map((v, i) => i === variableIndex ? { ...v, description: e.target.value } : v) })} />
          <Input maxLength={2000} placeholder={t('mcpManagement.defaultValue')} value={variable.defaultValue} disabled={variable.secret}
            onChange={e => update(index, { variables: draft.variables.map((v, i) => i === variableIndex ? { ...v, defaultValue: e.target.value } : v) })} />
          <div className="flex items-center gap-3 pb-2 text-sm">
            <label className="flex items-center gap-1"><input type="checkbox" checked={variable.required}
              onChange={e => update(index, { variables: draft.variables.map((v, i) => i === variableIndex ? { ...v, required: e.target.checked } : v) })} />{t('mcpManagement.required')}</label>
            <label className="flex items-center gap-1"><input type="checkbox" checked={variable.secret}
              onChange={e => update(index, { variables: draft.variables.map((v, i) => i === variableIndex ? { ...v, secret: e.target.checked, defaultValue: e.target.checked ? '' : v.defaultValue } : v) })} />{t('mcpManagement.secret')}</label>
            <Button type="button" variant="ghost" size="sm" aria-label={t('mcpManagement.removeVariable')}
              onClick={() => update(index, { variables: draft.variables.filter((_, i) => i !== variableIndex) })}>✕</Button>
          </div>
        </div>)}
        <Button type="button" variant="outline" size="sm" onClick={() => update(index, { variables: [...draft.variables, { name: '', description: '', required: true, secret: false, defaultValue: '' }] })}>{t('mcpManagement.addVariable')}</Button>
      </div>
      {previewConfiguration(draft, slug) && <details><summary className="cursor-pointer text-sm">{t('mcpManagement.preview')}</summary>
        <pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap break-all rounded bg-muted p-3 text-xs">{previewConfiguration(draft, slug)}</pre></details>}
      <Button type="button" variant="destructive" size="sm" onClick={() => setDrafts(drafts.filter((_, i) => i !== index))}>{t('mcpManagement.removeProfile')}</Button>
    </div>)}
    <Button type="button" variant="outline" size="sm" onClick={() => setDrafts([...drafts, emptyProfile()])}>{t('mcpManagement.addProfile')}</Button>
  </div>
}

function McpRevisionPanel({ entry, revision, direct }: { entry: McpEntry; revision: McpEntry['revisions'][number]; direct: boolean }) {
  const { t } = useTranslation()
  const command = useMcpCommand()
  const [notes, setNotes] = useState('')
  const [drafts, setDrafts] = useState<ProfileDraft[]>(() => revision.profiles.map(draftFromProfile))
  const [localCuration, setLocalCuration] = useState(revision.localCuration ?? '')
  const draft = revision.status === 'DRAFT'
  const reviewable = revision.status === 'PENDING_REVIEW'
  return <article className="rounded-lg border p-4"><details>
    <summary className="cursor-pointer font-semibold">{t('mcpManagement.revisionNumber', { number: revision.revisionNumber })}<span className="ml-3 text-sm font-normal">{t(`mcpManagement.status.${revision.status}`)} · {t(`mcpManagement.sourceKind.${revision.sourceKind}`)}</span></summary>
    <div className="mt-4 space-y-4">
      {revision.description && <p className="whitespace-pre-wrap text-sm">{revision.description}</p>}
      {revision.localCuration && !draft && <p className="whitespace-pre-wrap text-sm text-muted-foreground">{revision.localCuration}</p>}
      {revision.profiles.map(profile => <div key={profile.profileKey} className="space-y-1 text-sm">
        <p className="font-medium">{profile.displayName || profile.profileKey} · {t(`mcpDetail.transports.${profile.transportType}`)}</p>
        {(profile.variables?.length ?? 0) > 0 && <p className="text-xs text-muted-foreground">{profile.variables.map(variable =>
          `${variable.name}${variable.secret ? ' · ' + t('mcpManagement.secret') : variable.defaultValue ? ` = ${variable.defaultValue}` : ''}`).join(', ')}</p>}
      </div>)}
      {revision.reviews.map((review, index) => <p key={index} className="whitespace-pre-wrap text-sm text-muted-foreground">{review.reviewerId}: {review.notes}</p>)}
      {draft && entry.canManage && <>
        <McpProfilesEditor drafts={drafts} setDrafts={setDrafts} slug={entry.slug} />
        <label className="block space-y-1 text-sm">{t('mcpManagement.localCuration')}<Textarea maxLength={30000} value={localCuration} onChange={e => setLocalCuration(e.target.value)} /></label>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" disabled={command.isPending} onClick={() => command.mutate({ path: `/revisions/${revision.id}`, method: 'PUT', body: { profiles: drafts.map(toInput), localCuration: localCuration || undefined } })}>{t('mcpManagement.saveDraft')}</Button>
          <Button disabled={command.isPending || drafts.length === 0} onClick={() => command.mutate({ path: `/revisions/${revision.id}/submit` })}>{t('mcpManagement.submit')}</Button>
          <Button variant="destructive" disabled={command.isPending} onClick={() => command.mutate({ path: `/revisions/${revision.id}`, method: 'DELETE' })}>{t('mcpManagement.deleteDraft')}</Button>
        </div>
      </>}
      {reviewable && entry.canManage && <Button variant="outline" disabled={command.isPending} onClick={() => command.mutate({ path: `/revisions/${revision.id}/withdraw` })}>{t('mcpManagement.withdraw')}</Button>}
      {reviewable && entry.canReview && <form className="space-y-3 border-t pt-4" onSubmit={e => { e.preventDefault(); command.mutate({ path: `/revisions/${revision.id}/approve`, body: { notes } }) }}>
        <label className="block space-y-1 text-sm">{t('mcpManagement.reviewNotes')}<Textarea maxLength={10000} value={notes} onChange={e => setNotes(e.target.value)} /></label>
        <div className="flex flex-wrap gap-2">
          <Button type="submit" disabled={command.isPending}>{t('mcpManagement.approve')}</Button>
          <Button variant="destructive" disabled={command.isPending || !notes.trim()} onClick={() => command.mutate({ path: `/revisions/${revision.id}/reject`, body: { notes } })}>{t('mcpManagement.reject')}</Button>
        </div>
      </form>}
      {draft && entry.canReview && direct && <Button disabled={command.isPending} onClick={() => command.mutate({ path: `/revisions/${revision.id}/direct-publish` })}>{t('mcpManagement.directPublish')}</Button>}
      {command.isPending && <p role="status" className="text-sm">{t('common.loading')}</p>}
      {command.error && <p role="alert" className="text-sm text-destructive">{command.error.message}</p>}
    </div>
  </details></article>
}

function McpGovernancePanel({ entryId }: { entryId: number }) {
  const { t } = useTranslation()
  const command = useMcpCommand()
  const [action, setAction] = useState('HIDE')
  const [visibility, setVisibility] = useState<'PUBLIC' | 'NAMESPACE_ONLY' | 'PRIVATE'>('PUBLIC')
  const [reason, setReason] = useState('')
  return <form className="space-y-3 border-t pt-4" onSubmit={e => { e.preventDefault(); command.mutate({ path: `/entries/${entryId}/governance`, body: { action, reason, visibility: action === 'VISIBILITY' ? visibility : undefined } }) }}>
    <div className="grid gap-3 sm:grid-cols-2">
      <label className="space-y-1 text-sm">{t('mcpManagement.governanceAction')}<select className={selectClass} value={action} onChange={e => setAction(e.target.value)}>
        {['HIDE', 'SHOW', 'ARCHIVE', 'RESTORE', 'VISIBILITY'].map(value => <option key={value} value={value}>{t(`mcpManagement.governanceValues.${value}`)}</option>)}
      </select></label>
      {action === 'VISIBILITY' && <label className="space-y-1 text-sm">{t('mcpManagement.visibility')}<select className={selectClass} value={visibility} onChange={e => {
        const value = e.target.value
        if (value === 'PRIVATE' || value === 'NAMESPACE_ONLY' || value === 'PUBLIC') setVisibility(value)
      }}>
        {['PRIVATE', 'NAMESPACE_ONLY', 'PUBLIC'].map(value => <option key={value} value={value}>{t(`mcpManagement.visibilityValues.${value}`)}</option>)}
      </select></label>}
    </div>
    <label className="block space-y-1 text-sm">{t('mcpManagement.governanceReason')}<Textarea required maxLength={10000} value={reason} onChange={e => setReason(e.target.value)} /></label>
    <Button type="submit" variant="outline" disabled={command.isPending}>{t('mcpManagement.govern')}</Button>
  </form>
}
