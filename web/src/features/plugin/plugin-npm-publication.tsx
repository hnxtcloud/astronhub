import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { components } from '@/api/generated/schema'
import { fetchJson, getCsrfHeaders } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'

type Registry = components['schemas']['RegistryView']
type Settings = components['schemas']['SettingsView']
type Upload = components['schemas']['DependencyUploadView']
type Channel = components['schemas']['ChannelView']
type Prerequisites = components['schemas']['PrerequisitesView']
type RegistryRequest = components['schemas']['RegistryRequest']
const base = '/api/v1/plugin-management'
const selectClass = 'h-10 w-full rounded-md border border-input bg-background px-3 text-sm'
function send<T>(path: string, body?: unknown, method = 'POST') {
  return fetchJson<T>(base + path, { method, headers: getCsrfHeaders({ 'Content-Type': 'application/json' }), body: body === undefined ? undefined : JSON.stringify(body), timeoutMs: 300000 })
}

export function PluginNpmAdminPanel() {
  const { t } = useTranslation()
  const cache = useQueryClient()
  const settings = useQuery({ queryKey: ['plugin-npm', 'settings'], queryFn: () => fetchJson<Settings>(base + '/npm/settings') })
  const uploads = useQuery({ queryKey: ['plugin-npm', 'uploads'], queryFn: () => fetchJson<Upload[]>(base + '/npm/dependencies') })
  const [name, setName] = useState('')
  const [registry, setRegistry] = useState('')
  const [ref, setRef] = useState('')
  const [defaultRegistry, setDefault] = useState(true)
  const [target, setTarget] = useState('')
  const [source, setSource] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const configure = useMutation({ mutationFn: () => {
    const request: RegistryRequest = { name, registry, credentialRef: ref, defaultRegistry }
    return send<Registry>('/npm/registries', request)
  }, onSuccess: () => cache.invalidateQueries({ queryKey: ['plugin-npm'] }) })
  const upload = useMutation({ mutationFn: async () => {
    const failures: string[] = []
    for (const file of files) { try { await fetchJson<Upload>(`${base}/npm/dependencies?registryId=${target}&source=${encodeURIComponent(source)}`, { method: 'POST', headers: getCsrfHeaders({ 'Content-Type': 'application/octet-stream' }), body: file, timeoutMs: 300000 }) } catch (error) { failures.push(`${file.name}: ${error instanceof Error ? error.message : t('pluginNpm.failed')}`) } }
    if (failures.length) throw new Error(failures.join('\n'))
  }, onSettled: () => cache.invalidateQueries({ queryKey: ['plugin-npm', 'uploads'] }) })
  const publish = useMutation({ mutationFn: (id: number) => send<Upload>(`/npm/dependencies/${id}/publish`), onSuccess: () => cache.invalidateQueries({ queryKey: ['plugin-npm', 'uploads'] }) })
  function save(event: FormEvent) { event.preventDefault(); configure.mutate() }
  return <details className="rounded-lg border p-4">
    <summary className="cursor-pointer font-medium">{t('pluginNpm.title')}</summary>
    <p className="my-3 max-w-prose text-sm text-muted-foreground">{t('pluginNpm.description')}</p>
    <form onSubmit={save} className="grid gap-3 sm:grid-cols-2">
      <label className="space-y-1 text-sm">{t('pluginNpm.name')}<Input required maxLength={120} value={name} onChange={e => setName(e.target.value)} /></label>
      <label className="space-y-1 text-sm">{t('pluginNpm.registry')}<select required className={selectClass} value={registry} onChange={e => setRegistry(e.target.value)}><option value="">{t('pluginNpm.select')}</option>{settings.data?.allowedRegistries?.map(value => <option key={value}>{value}</option>)}</select></label>
      <label className="space-y-1 text-sm">{t('pluginNpm.credentialRef')}<select required className={selectClass} value={ref} onChange={e => setRef(e.target.value)}><option value="">{t('pluginNpm.select')}</option>{settings.data?.credentialRefs?.map(value => <option key={value}>{value}</option>)}</select></label>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={defaultRegistry} onChange={e => setDefault(e.target.checked)} />{t('pluginNpm.default')}</label>
      <div><Button disabled={configure.isPending}>{t('pluginNpm.save')}</Button></div>
    </form>
    {settings.data?.registries?.map(value => <p className="mt-2 text-sm" key={value.id}>{value.id}: {value.name} · {value.registry} · {value.credentialRef} · {t(value.credentialAvailable ? 'pluginNpm.ready' : 'pluginNpm.missingCredential')}</p>)}
    {(settings.data?.allowedRegistries?.length ?? 0) === 0 && <p className="my-3 text-sm">{t('pluginNpm.operatorConfiguration')}</p>}
    <form onSubmit={event => { event.preventDefault(); upload.mutate() }} className="mt-5 space-y-3 border-t pt-4">
      <h3 className="font-medium">{t('pluginNpm.dependencies')}</h3>
      <p className="text-sm text-muted-foreground">{t('pluginNpm.dependencyHelp')}</p>
      <label className="block space-y-1 text-sm">{t('pluginNpm.registry')}<select required className={selectClass} value={target} onChange={e => setTarget(e.target.value)}><option value="">{t('pluginNpm.select')}</option>{settings.data?.registries?.map(value => <option key={value.id} value={value.id}>{value.name} · {value.registry}</option>)}</select></label>
      <label className="block space-y-1 text-sm">{t('pluginNpm.source')}<Input required maxLength={2000} value={source} onChange={e => setSource(e.target.value)} /></label>
      <label className="block space-y-1 text-sm">{t('pluginNpm.archive')}<Input type="file" accept=".tgz,application/gzip" multiple required onChange={e => setFiles(Array.from(e.target.files ?? []))} /></label>
      <Button disabled={upload.isPending || files.length === 0}>{t('pluginNpm.upload')}</Button>
    </form>
    <div className="mt-4 space-y-3">{uploads.data?.map(value => <article key={value.id} className="rounded border p-3 text-sm">
      <p className="font-medium">{value.name}@{value.version} · {t(`publicationLabels.states.${value.state}`)}</p><p>{value.registry} · {value.uploadedBy} · {value.source}</p>
      <p className="break-all font-mono text-xs">SHA256: {value.sha256}</p>
      <p>{t('pluginNpm.attempts')}: {value.attemptCount}</p>
      {value.lastError && <p role="alert" className="text-destructive">{value.lastError}</p>}
      <details><summary>{t('pluginNpm.inspect')}</summary><pre className="overflow-auto whitespace-pre-wrap text-xs">{value.requirementsJson}</pre>{value.receipt && <details><summary className="cursor-pointer">{t('pluginNpm.inspect')}</summary><pre className="mt-2 overflow-auto whitespace-pre-wrap text-xs">{value.receipt}</pre></details>}</details>
      {value.state !== 'SUCCEEDED' && <Button className="mt-2" variant="outline" disabled={publish.isPending || !value.id} onClick={() => value.id && publish.mutate(value.id)}>{t('pluginNpm.confirm')}</Button>}
    </article>)}</div>
    {[settings.error, uploads.error, configure.error, upload.error, publish.error].filter(Boolean).map((error, index) => <p role="alert" className="mt-2 text-sm text-destructive" key={index}>{error instanceof Error ? error.message : t('pluginNpm.failed')}</p>)}
  </details>
}

export function PluginNpmReleasePanel({ releaseId, canManage, canReview, draft, approved, hasPi }: { releaseId: number; canManage: boolean; canReview: boolean; draft: boolean; approved: boolean; hasPi: boolean }) {
  const { t } = useTranslation()
  const cache = useQueryClient()
  const [registryId, setRegistryId] = useState('')
  const registries = useQuery({ queryKey: ['plugin-npm', 'registries'], queryFn: () => fetchJson<Registry[]>(base + '/npm/registries'), enabled: draft && canManage && hasPi })
  const channels = useQuery({ queryKey: ['plugin-npm', 'channels', releaseId], queryFn: () => fetchJson<Channel[]>(`${base}/releases/${releaseId}/publication-channels`), enabled: approved, refetchInterval: query => query.state.data?.some(value => value.state !== 'SUCCEEDED' && value.state !== 'BLOCKED') ? 5000 : false })
  const select = useMutation({ mutationFn: () => send<Registry>(`/releases/${releaseId}/npm-registry`, { registryId: Number(registryId) } satisfies components['schemas']['RegistrySelection'], 'PUT') })
  const prerequisites = useMutation({ mutationFn: () => send<Prerequisites>(`/releases/${releaseId}/npm-prerequisites`) })
  const retry = useMutation({ mutationFn: () => send<Channel[]>(`/releases/${releaseId}/publish-channels`), onSuccess: () => Promise.all([cache.invalidateQueries({ queryKey: ['plugin-management'] }), cache.invalidateQueries({ queryKey: ['plugin-npm', 'channels', releaseId] })]) })
  return <section className="space-y-3 rounded border p-3 text-sm">
    <h5 className="font-medium">{t('pluginNpm.publication')}</h5>
    {hasPi && draft && canManage && <div className="flex flex-wrap gap-2"><select aria-label={t('pluginNpm.registry')} className={selectClass} value={registryId} onChange={e => setRegistryId(e.target.value)}><option value="">{t('pluginNpm.defaultSelection')}</option>{registries.data?.map(value => <option key={value.id} value={value.id}>{value.name} · {value.registry}</option>)}</select><Button variant="outline" disabled={!registryId || select.isPending} onClick={() => select.mutate()}>{t('pluginNpm.selectRegistry')}</Button>{select.isSuccess && <p role="status">{t('pluginNpm.saved')}</p>}</div>}
    {channels.data?.map(value => <div key={value.channel}><p>{value.channel} · {t(`publicationLabels.states.${value.state}`)} · {t('pluginNpm.attempts')}: {value.attemptCount}</p>{value.lastError && <p className="text-destructive">{value.lastError}</p>}{value.receipt && <details><summary className="cursor-pointer">{t('pluginNpm.inspect')}</summary><pre className="mt-2 overflow-auto whitespace-pre-wrap text-xs">{value.receipt}</pre></details>}</div>)}
    {channels.data?.some(value => value.state === 'BLOCKED') && <p>{t('pluginNpm.blocked')}</p>}
    {canReview && channels.data?.some(value => value.state !== 'SUCCEEDED' && value.state !== 'BLOCKED') && <Button variant="outline" disabled={retry.isPending} onClick={() => retry.mutate()}>{t('pluginNpm.retry')}</Button>}
    {hasPi && <Button variant="outline" disabled={prerequisites.isPending} onClick={() => prerequisites.mutate()}>{t('pluginNpm.check')}</Button>}
    {prerequisites.data && <div className="space-y-2"><p>{t('pluginNpm.offlineCaution')}</p><p>{prerequisites.data.registry ?? t('pluginNpm.noRegistry')}</p>{prerequisites.data.dependencies?.map((value, index) => <p key={index}>{value.name}@{value.range} · {t(`publicationLabels.dependencyKinds.${value.kind}`)} · {t(`publicationLabels.availability.${value.availability}`)}</p>)}<details><summary className="cursor-pointer">{t('pluginNpm.inspect')}</summary><pre className="mt-2 overflow-auto whitespace-pre-wrap text-xs">{prerequisites.data.requirementsJson}</pre></details>{prerequisites.data.installCommand && <><p>{t('pluginNpm.installHelp')}</p><code className="block break-all">{prerequisites.data.registryConfiguration} {prerequisites.data.installCommand}</code></>}</div>}
    {[channels.error, select.error, prerequisites.error, retry.error].filter(Boolean).map((error, index) => <p role="alert" className="text-destructive" key={index}>{error instanceof Error ? error.message : t('pluginNpm.failed')}</p>)}
  </section>
}
