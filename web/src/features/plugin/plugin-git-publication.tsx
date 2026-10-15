import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { components } from '@/api/generated/schema'
import { fetchJson, getCsrfHeaders } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'

type Settings = components['schemas']['GitSettingsView']
type Target = components['schemas']['GitTargetView']
type Intent = components['schemas']['GitIntentView']
const base = '/api/v1/plugin-management'
const selectClass = 'h-10 w-full rounded-md border border-input bg-background px-3 text-sm'
export function PluginGitAdminPanel() {
  const { t } = useTranslation()
  const cache = useQueryClient()
  const settings = useQuery({ queryKey: ['plugin-git', 'settings'], queryFn: () => fetchJson<Settings>(base + '/git/settings') })
  const [namespaceId, setNamespace] = useState('')
  const [remote, setRemote] = useState('')
  const [fetchUrl, setFetchUrl] = useState('')
  const [branch, setBranch] = useState('main')
  const [credentialRef, setCredential] = useState('')
  const configure = useMutation({ mutationFn: () => {
    const body: components['schemas']['GitTargetRequest'] = { namespaceId: Number(namespaceId), remote, fetchUrl: fetchUrl || remote, branch, credentialRef }
    return fetchJson<Target>(base + '/git/targets', { method: 'POST', headers: getCsrfHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(body) })
  }, onSuccess: () => cache.invalidateQueries({ queryKey: ['plugin-git'] }) })
  function save(event: FormEvent) { event.preventDefault(); configure.mutate() }
  return <details className="rounded-lg border p-4">
    <summary className="cursor-pointer font-medium">{t('pluginGit.title')}</summary>
    <p className="my-3 max-w-prose text-sm text-muted-foreground">{t('pluginGit.description')}</p>
    <form onSubmit={save} className="grid gap-3 sm:grid-cols-2">
      <label className="space-y-1 text-sm">{t('pluginGit.namespace')}<select required className={selectClass} value={namespaceId} onChange={e => setNamespace(e.target.value)}><option value="">{t('pluginGit.select')}</option>{settings.data?.namespaces?.map(value => <option key={value.id} value={value.id}>{value.displayName} · {value.slug}</option>)}</select></label>
      <label className="space-y-1 text-sm">{t('pluginGit.remote')}<select required className={selectClass} value={remote} onChange={e => setRemote(e.target.value)}><option value="">{t('pluginGit.select')}</option>{settings.data?.allowedRemotes?.map(value => <option key={value}>{value}</option>)}</select></label>
      <label className="space-y-1 text-sm">{t('pluginGit.fetchUrl')}<select className={selectClass} value={fetchUrl} onChange={e => setFetchUrl(e.target.value)}><option value="">{t('pluginGit.sameRemote')}</option>{settings.data?.allowedRemotes?.map(value => <option key={value}>{value}</option>)}</select></label>
      <label className="space-y-1 text-sm">{t('pluginGit.branch')}<Input required maxLength={200} value={branch} onChange={e => setBranch(e.target.value)} /></label>
      <label className="space-y-1 text-sm">{t('pluginGit.credential')}<select required className={selectClass} value={credentialRef} onChange={e => setCredential(e.target.value)}><option value="">{t('pluginGit.select')}</option>{settings.data?.credentialRefs?.map(value => <option key={value}>{value}</option>)}</select></label>
      <div className="flex items-end"><Button disabled={configure.isPending}>{t('pluginGit.save')}</Button></div>
    </form>
    {(settings.data?.allowedRemotes?.length ?? 0) === 0 && <p className="my-3 text-sm">{t('pluginGit.operatorConfiguration')}</p>}
    {configure.isSuccess && <p role="status" className="mt-3 text-sm">{t('pluginGit.saved')}</p>}
    <div className="mt-4 space-y-3">{settings.data?.targets?.map(value => <article key={value.id} className="rounded border p-3 text-sm">
      <p className="font-medium">@{value.namespace} · {t(value.selected ? 'pluginGit.current' : 'pluginGit.historical')} · #{value.id}</p>
      <p className="break-all">{value.remote} · {value.branch}</p><p>{value.credentialRef} · {t(value.credentialAvailable ? 'pluginGit.ready' : 'pluginGit.unavailable')}</p>
      <p className="text-muted-foreground">{t('pluginGit.historyCaution')}</p>
    </article>)}</div>
    {[settings.error, configure.error].filter(Boolean).map((error, index) => <p key={index} role="alert" className="mt-2 text-sm text-destructive">{error instanceof Error ? error.message : t('pluginGit.failed')}</p>)}
  </details>
}
export function PluginGitEvidencePanel({ releaseId, approved, completed }: { releaseId: number; approved: boolean; completed: boolean }) {
  const { t } = useTranslation()
  const intents = useQuery({ queryKey: ['plugin-git', 'intents', releaseId, completed], enabled: approved, queryFn: () => fetchJson<Intent[]>(`${base}/releases/${releaseId}/git-intents`), refetchInterval: completed ? false : 10000 })
  if (!approved) return null
  return <details className="rounded border p-3 text-sm">
    <summary className="cursor-pointer font-medium">{t('pluginGit.intents')}</summary>
    <p className="my-2 text-muted-foreground">{t('pluginGit.intentHelp')}</p>
    {intents.data?.map(value => <dl key={value.id} className="my-3 space-y-1 border-t pt-2 text-xs"><dt>{t('pluginGit.commit')}</dt><dd className="break-all font-mono">{value.commit}</dd><dt>{t('pluginGit.parent')}</dt><dd className="break-all font-mono">{value.parent ?? t('pluginGit.initial')}</dd><dt>{t('pluginGit.tree')}</dt><dd className="break-all font-mono">{value.tree}</dd><dt>{t('pluginGit.plan')}</dt><dd className="break-all font-mono">{value.planDigest}</dd></dl>)}
    {intents.error && <p role="alert" className="text-destructive">{intents.error instanceof Error ? intents.error.message : t('pluginGit.failed')}</p>}
  </details>
}
