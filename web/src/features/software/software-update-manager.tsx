import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { softwareUpdates, type UpdateRelease } from '@/api/software-updates'
import type { SoftwarePackage } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { withBasePath } from '@/shared/lib/base-path'

const updateKey = ['software', 'updates'] as const
const targets = ['WINDOWS', 'LINUX', 'MACOS']
const arches = ['X86_64', 'ARM64', 'X86', 'UNIVERSAL']

export function SoftwareUpdateSourceManager({ appId, appSlug }: { appId: number; appSlug: string }) {
  const { t } = useTranslation()
  const cache = useQueryClient()
  const [slug, setSlug] = useState('windows-stable')
  const [project, setProject] = useState('')
  const [target, setTarget] = useState('')
  const source = useQuery({ queryKey: [...updateKey, 'source', appId], queryFn: () => softwareUpdates.source(appId) })
  const published = useQuery({ queryKey: [...updateKey, 'published', appId], queryFn: () => softwareUpdates.published(appId) })
  const mutation = useMutation({ mutationFn: async (operation: () => Promise<unknown>) => operation(), onSuccess: () => cache.invalidateQueries({ queryKey: updateKey }) })
  const selected = published.data?.find(release => release.id === Number(target))
  return <section className="space-y-3 rounded border p-3">
    <h3 className="font-medium">{t('softwareUpdates.sourceTitle')}</h3>
    <p className="text-sm text-muted-foreground">{t('softwareUpdates.anonymousHint')}</p>
    {source.data && !source.data.anonymousEnabled && <p role="status" className="text-sm">{t('softwareUpdates.sourceDisabled')}</p>}
    <p className="text-sm text-muted-foreground">{t('softwareUpdates.migrationHint')}</p>
    <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={source.data?.anonymousEnabled ?? false} disabled={!source.data || mutation.isPending} onChange={event => { const enabled = event.currentTarget.checked; mutation.mutate(() => softwareUpdates.anonymous(appId, enabled)) }} />{t('softwareUpdates.anonymous')}</label>
    <div className="grid gap-3 sm:grid-cols-3">
      <label className="text-sm">{t('softwareUpdates.channelSlug')}<Input value={slug} onChange={event => setSlug(event.target.value)} /></label>
      <label className="text-sm">{t('softwareUpdates.project')}<Input value={project} onChange={event => setProject(event.target.value)} placeholder="apps/intelli-dev" /></label>
      <label className="text-sm">{t('softwareUpdates.target')}<select className="block w-full rounded border bg-background p-2" value={target} onChange={event => setTarget(event.target.value)}><option value="">{t('softwareUpdates.chooseTarget')}</option>{published.data?.map(release => <option key={release.id} value={release.id}>{release.tag} · {release.targetOs}/{release.targetArch} · {release.channel}/{release.variant}</option>)}</select></label>
    </div>
    <Button size="sm" disabled={mutation.isPending || !slug || !project || !selected} onClick={() => mutation.mutate(() => softwareUpdates.channel(appId, { slug, virtualProjectPath: project, targetOs: selected?.targetOs, targetArch: selected?.targetArch, channel: selected?.channel, variant: selected?.variant, targetReleaseId: selected?.id }))}>{t('softwareUpdates.saveChannel')}</Button>
    <ul className="space-y-2 text-sm">{source.data?.channels.map(channel => <li key={channel.id} className="flex flex-wrap items-center gap-2"><code>{channel.slug} → {channel.virtualProjectPath} → #{channel.targetReleaseId ?? '—'}</code><Button size="sm" variant="ghost" onClick={() => { setSlug(channel.slug); setProject(channel.virtualProjectPath); setTarget(String(channel.targetReleaseId ?? '')) }}>{t('softwareUpdates.editChannel')}</Button><Button size="sm" variant="ghost" disabled={mutation.isPending || !channel.targetReleaseId} onClick={() => mutation.mutate(() => softwareUpdates.channel(appId, { ...channel, targetReleaseId: undefined }))}>{t('softwareUpdates.clearTarget')}</Button></li>)}</ul>
    {source.data?.channels.map(channel => {
      const release = published.data?.find(item => item.id === channel.targetReleaseId)
      if (!release) return null
      const base = new URL(withBasePath(`/updates/${encodeURIComponent(appSlug)}/${encodeURIComponent(channel.slug)}/`), window.location.origin).href
      const file = release.protocol === 'BAIZE' ? 'latest.json' : `${release.channel === 'stable' ? 'latest' : 'preview'}${release.targetOs === 'MACOS' ? '-mac' : ''}.yml`
      const configuration = release.protocol === 'BAIZE'
        ? `BAIZELINGTAI_UPDATE_FEED_URL=${base}${file}`
        : JSON.stringify({ provider: 'generic', url: base, ...(release.channel === 'preview' ? { channel: 'preview' } : {}) }, null, 2)
      const host = new URL(withBasePath('/'), window.location.origin).href
      const projectParts = channel.virtualProjectPath.split('/')
      const candidates = release.assets?.filter(asset => asset.role === 'METADATA'
        && (asset.relativePath === file || asset.relativePath.endsWith(`/${file}`))) ?? []
      const metadata = candidates.length === 1 ? candidates[0] : undefined
      const artifactPath = metadata?.relativePath.includes('/') ? metadata.relativePath.slice(0, metadata.relativePath.lastIndexOf('/')) : ''
      return <div key={channel.id} className="space-y-2 rounded border p-3 text-sm">
        <strong>{channel.slug} · {release.targetOs}/{release.targetArch} · {release.channel}/{release.variant}</strong>
        <label className="block">{t('softwareUpdates.feedUrl')}<Input readOnly value={`${base}${file}`} /></label>
        <pre className="overflow-x-auto rounded bg-muted p-2">{configuration}</pre>
        {release.protocol === 'ELECTRON' && release.channel === 'preview' && <p className="text-muted-foreground">{t('softwareUpdates.previewHint')}</p>}
        {release.protocol === 'ELECTRON' && release.channel === 'stable' && !metadata && <p className="text-muted-foreground">{t('softwareUpdates.gitlabMetadataRequired')}</p>}
        {release.protocol === 'ELECTRON' && release.channel === 'stable' && metadata && <>
          <p className="text-muted-foreground">{t('softwareUpdates.gitlabConfiguration')}</p>
          <pre className="overflow-x-auto rounded bg-muted p-2">{JSON.stringify({ provider: 'gitlab', baseUrl: host.replace(/\/$/, ''), owner: projectParts.slice(0, -1).join('/'), repo: projectParts.at(-1), artifactPath }, null, 2)}</pre>
        </>}
      </div>
    })}
    {(source.error || published.error || mutation.error) && <p role="alert" className="text-sm text-destructive">{(source.error || published.error || mutation.error)?.message}</p>}
  </section>
}

/** Original artifacts are frozen only after explicit validation and publication. */
export function SoftwareUpdateManager({ release }: { release: { id: number; packages: SoftwarePackage[] } }) {
  const { t } = useTranslation()
  const cache = useQueryClient()
  const [version, setVersion] = useState('')
  const [os, setOs] = useState('WINDOWS')
  const [arch, setArch] = useState('X86_64')
  const [channel, setChannel] = useState('stable')
  const [variant, setVariant] = useState('default')
  const [protocol, setProtocol] = useState('ELECTRON')
  const [piVersion, setPiVersion] = useState('')
  const drafts = useQuery({ queryKey: [...updateKey, release.id], queryFn: () => softwareUpdates.list(release.id) })
  const create = useMutation({ mutationFn: () => softwareUpdates.create({ catalogReleaseId: release.id, machineVersion: version, tag: `v${version}`, targetOs: os, targetArch: arch, channel, variant, protocol, piVersion: piVersion || undefined }),
    onSuccess: () => cache.invalidateQueries({ queryKey: updateKey }) })
  const select = (label: string, value: string, values: string[], change: (value: string) => void) => <label className="space-y-1 text-sm">{t(`softwareUpdates.${label}`)}<select className="block w-full rounded border bg-background p-2" value={value} onChange={event => change(event.target.value)}>{values.map(item => <option key={item}>{item}</option>)}</select></label>
  return <section className="space-y-3 border-t pt-3">
    <h3 className="font-medium">{t('softwareUpdates.title')}</h3>
    <p className="text-sm text-muted-foreground">{t('softwareUpdates.immutableHint')}</p>
    <p className="text-sm text-muted-foreground">{t('softwareUpdates.nativeFormats')}</p>
    <div className="grid gap-3 sm:grid-cols-3">
      <label className="text-sm">{t('softwareUpdates.machineVersion')}<Input value={version} onChange={event => setVersion(event.target.value)} placeholder="1.2.3" /></label>
      {select('os', os, targets, setOs)}{select('arch', arch, arches, setArch)}
      {select('channel', channel, ['stable', 'preview'], setChannel)}
      <label className="text-sm">{t('softwareUpdates.variant')}<Input value={variant} onChange={event => setVariant(event.target.value)} /></label>
      {select('protocol', protocol, ['ELECTRON', 'BAIZE'], setProtocol)}
      {protocol === 'BAIZE' && <label className="text-sm">{t('softwareUpdates.piVersion')}<Input value={piVersion} onChange={event => setPiVersion(event.target.value)} /></label>}
    </div>
    <Button size="sm" disabled={create.isPending || !version} onClick={() => create.mutate()}>{t('softwareUpdates.create')}</Button>
    {(create.error || drafts.error) && <p role="alert" className="text-sm text-destructive">{(create.error || drafts.error)?.message}</p>}
    {drafts.data?.map(snapshot => <Snapshot key={snapshot.id} snapshot={snapshot} packages={release.packages} />)}
  </section>
}

function Snapshot({ snapshot, packages }: { snapshot: UpdateRelease; packages: SoftwarePackage[] }) {
  const { t } = useTranslation()
  const cache = useQueryClient()
  const [packageId, setPackageId] = useState('')
  const [path, setPath] = useState('')
  const [role, setRole] = useState('METADATA')
  const [files, setFiles] = useState<File[]>([])
  const [validatedAssetIds, setValidatedAssetIds] = useState<string | null>(null)
  const assetIds = snapshot.assets.map(asset => asset.id).join(',')
  const refresh = () => cache.invalidateQueries({ queryKey: updateKey })
  const mutation = useMutation({ mutationFn: async (operation: () => Promise<unknown>) => operation(), onSuccess: refresh })
  const draft = snapshot.status === 'DRAFT'
  const applicable = packages.filter(pkg => pkg.status === 'PUBLISHED' && pkg.confirmedBy && pkg.targetOs.toUpperCase() === snapshot.targetOs && pkg.targetArch.toUpperCase() === snapshot.targetArch)
  return <article className="space-y-3 rounded border p-3">
    <div className="flex flex-wrap gap-3 text-sm"><strong>{snapshot.tag}</strong><span>{snapshot.targetOs}/{snapshot.targetArch} · {snapshot.channel} · {snapshot.variant}</span><span>{snapshot.status}</span></div>
    <ul className="space-y-1 text-sm">{snapshot.assets.map(asset => <li key={asset.id} className="flex flex-wrap items-center gap-2"><code>{asset.relativePath}</code><span>{asset.role} · {asset.byteSize} bytes</span>{draft && <Button size="sm" variant="ghost" disabled={mutation.isPending} onClick={() => mutation.mutate(() => softwareUpdates.remove(snapshot.id, asset.id))}>{t('softwareUpdates.remove')}</Button>}</li>)}</ul>
    {draft && <>
      <div className="flex flex-wrap gap-2">
        <label className="text-sm">{t('softwareUpdates.path')}<Input value={path} onChange={event => setPath(event.target.value)} placeholder="dist/app.zip" /></label>
        <label className="text-sm">{t('softwareUpdates.installer')}<select className="block rounded border bg-background p-2" value={packageId} onChange={event => setPackageId(event.target.value)}><option value="">{t('softwareUpdates.chooseInstaller')}</option>{applicable.map(pkg => <option key={pkg.id} value={pkg.id}>{pkg.fileName} (#{pkg.id})</option>)}</select></label>
        <Button size="sm" disabled={mutation.isPending || !packageId || !path} onClick={() => mutation.mutate(async () => { await softwareUpdates.link(snapshot.id, Number(packageId), path); setPath('') })}>{t('softwareUpdates.link')}</Button>
      </div>
      <p className="text-xs text-muted-foreground">{t('softwareUpdates.uploadHint')}</p>
      <div className="flex flex-wrap items-end gap-2">
        <label className="text-sm">{t('softwareUpdates.role')}<select className="block rounded border bg-background p-2" value={role} onChange={event => setRole(event.target.value)}>{['METADATA', 'BLOCKMAP', 'CHECKSUM', 'SIGNATURE', 'OTHER'].map(value => <option key={value}>{value}</option>)}</select></label>
        <label className="text-sm">{t('softwareUpdates.files')}<input type="file" multiple onChange={event => setFiles(Array.from(event.target.files ?? []))} className="block max-w-xs text-sm" /></label>
        <Button size="sm" disabled={mutation.isPending || !files.length} onClick={() => mutation.mutate(async () => {
          for (const file of files) await softwareUpdates.upload(snapshot.id, files.length === 1 && path ? path : `${path ? `${path.replace(/\/$/, '')}/` : ''}${file.name}`, role, file)
          setFiles([])
        })}>{t('softwareUpdates.upload')}</Button>
      </div>
      <Button size="sm" variant="outline" disabled={mutation.isPending} onClick={() => mutation.mutate(async () => { await softwareUpdates.action(snapshot.id, 'validate'); setValidatedAssetIds(assetIds) })}>{t('softwareUpdates.validate')}</Button>{' '}
      <Button size="sm" disabled={mutation.isPending} onClick={() => mutation.mutate(() => softwareUpdates.action(snapshot.id, 'publish'))}>{t('softwareUpdates.publish')}</Button>
      {validatedAssetIds === assetIds && <p role="status" className="text-sm">{t('softwareUpdates.valid')}</p>}
    </>}
    {snapshot.status === 'PUBLISHED' && <Button size="sm" variant="outline" disabled={mutation.isPending} onClick={() => mutation.mutate(() => softwareUpdates.action(snapshot.id, 'withdraw'))}>{t('softwareUpdates.withdraw')}</Button>}
    {mutation.error && <p role="alert" className="text-sm text-destructive">{mutation.error.message}</p>}
  </article>
}
