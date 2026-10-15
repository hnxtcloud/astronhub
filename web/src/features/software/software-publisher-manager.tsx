import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { softwarePublishers } from '@/api/software-updates'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { withBasePath } from '@/shared/lib/base-path'

/** Session administrators issue one app-bound credential; secrets stay in local memory only. */
export function SoftwarePublisherManager({ appId }: { appId: number }) {
  const { t } = useTranslation()
  const cache = useQueryClient()
  const key = ['software', 'publishers', appId] as const
  const [name, setName] = useState('')
  const [expiresOn, setExpiresOn] = useState('')
  const [secret, setSecret] = useState('')
  const grants = useQuery({ queryKey: key, queryFn: () => softwarePublishers.list(appId) })
  const create = useMutation({ mutationFn: async () => {
    const created = await softwarePublishers.create(appId, { name, expiresAt: `${expiresOn}T23:59:59Z` })
    setSecret(created.rawToken)
    // Return no secret-bearing data to the shared mutation cache.
  }, onSuccess: () => cache.invalidateQueries({ queryKey: key }) })
  const revoke = useMutation({ mutationFn: (id: number) => softwarePublishers.revoke(appId, id),
    onSuccess: () => cache.invalidateQueries({ queryKey: key }) })
  return <section className="space-y-3 rounded border p-3">
    <h3 className="font-medium">{t('softwarePublishers.title')}</h3>
    <p className="text-sm text-muted-foreground">{t('softwarePublishers.hint')}</p>
    <div className="flex flex-wrap items-end gap-3">
      <label className="text-sm">{t('softwarePublishers.name')}<Input value={name} maxLength={64} onChange={event => setName(event.target.value)} /></label>
      <label className="text-sm">{t('softwarePublishers.expires')}<Input type="date" value={expiresOn} onChange={event => setExpiresOn(event.target.value)} /></label>
      <Button size="sm" disabled={create.isPending || !name.trim() || !expiresOn || !!secret} onClick={() => create.mutate()}>{t('softwarePublishers.create')}</Button>
    </div>
    {secret && <div className="space-y-2 rounded border p-3">
      <p role="status" className="text-sm">{t('softwarePublishers.once')}</p>
      <label className="block text-sm">{t('softwarePublishers.secret')}<Input readOnly value={secret} autoComplete="off" spellCheck={false} /></label>
      <Button size="sm" variant="outline" onClick={() => setSecret('')}>{t('softwarePublishers.clear')}</Button>
    </div>}
    <code className="block break-all text-xs">python scripts/publish-software-release.py --server {new URL(withBasePath('/'), window.location.origin).href.replace(/\/$/, '')} --app {appId} --manifest release.json --state release-state.json --publish</code>
    <p className="text-xs text-muted-foreground">{t('softwarePublishers.toolHint')}</p>
    <ul className="space-y-2 text-sm">{grants.data?.map(grant => <li key={grant.tokenId} className="flex flex-wrap items-center gap-3">
      <strong>{grant.name}</strong><span>{grant.ownerUserId}</span><span>{grant.expiresAt}</span>
      {grant.revokedAt ? <span>{t('softwarePublishers.revoked')}</span>
        : <Button size="sm" variant="outline" disabled={revoke.isPending} onClick={() => revoke.mutate(grant.tokenId)}>{t('softwarePublishers.revoke')}</Button>}
    </li>)}</ul>
    {(grants.error || create.error || revoke.error) && <p role="alert" className="text-sm text-destructive">{(grants.error || create.error || revoke.error)?.message}</p>}
  </section>
}
