import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import { withBasePath } from '@/shared/lib/base-path'
import { ContentUseAction } from '@/shared/components/content-use-action'
import type { PluginProject, PluginRelease } from './plugin-api'
import {
  publishedPath,
  usePublishedPlugin,
  usePublishedPluginRelease,
  usePluginFiles,
  usePluginFile,
  usePluginInstructions,
  usePluginComponents,
  usePluginGovernance,
  type PluginComponent,
} from './plugin-access-api'

const selectClass =
  'h-10 rounded-md border border-input bg-background px-3 text-sm'
export function PluginPublishedDetail({
  namespace,
  slug,
}: {
  namespace: string
  slug: string
}) {
  const { t } = useTranslation()
  const plugin = usePublishedPlugin(namespace, slug)
  const [chosen, setChosen] = useState('')
  const versions = plugin.data?.versions ?? []
  const version =
    chosen ||
    versions.find((v) => v.id === plugin.data?.latestReleaseId)?.version ||
    versions[0]?.version ||
    ''
  const base = publishedPath(namespace, slug)
  const release = usePublishedPluginRelease(base, version)
  return (
    <div className="mx-auto max-w-5xl space-y-6 p-4 sm:p-6">
      {plugin.isLoading && <p role="status">{t('common.loading')}</p>}
      {plugin.error && <p role="alert">{plugin.error.message}</p>}
      {plugin.data && (
        <>
          <header>
            <p className="font-mono text-sm text-muted-foreground">
              @{namespace}/{slug}
            </p>
            <div className="mt-3 flex flex-wrap items-start justify-between gap-4"><h1 className="text-2xl font-semibold">
              {plugin.data.displayName}
            </h1>{!!release.data?.distributions.length && <ContentUseAction targetId="plugin-use" label={t('publication.actions.PLUGIN')} />}</div>
            <p className="mt-2 text-muted-foreground">{plugin.data.summary}</p>
          </header>
          <label className="flex flex-wrap items-center gap-3 text-sm">
            {t('pluginAccess.publishedVersion')}
            <select
              className={selectClass}
              value={version}
              onChange={(e) => setChosen(e.target.value)}
            >
              {versions.map((v) => (
                <option key={v.id} value={v.version}>
                  {v.version}
                </option>
              ))}
            </select>
          </label>
          {release.error && <p role="alert">{release.error.message}</p>}
          {release.data && (
            <section id="plugin-use" className="scroll-mt-24 space-y-5">
              <p className="whitespace-pre-wrap text-sm">
                {release.data.changelog}
              </p>
              {release.data.distributions.map((d) => (
                <article
                  key={`${version}-${d.runtimeKey}`}
                  className="space-y-3 rounded-lg border p-4"
                >
                  <h2 className="font-semibold">
                    {d.runtimeKey}: {d.nativeName}@{d.nativeVersion}
                  </h2>
                  <p className="text-sm">
                    {t(`pluginManagement.evidence.${d.compatibilityEvidence}`)}{' '}
                    · {t(`pluginManagement.evidence.${d.securityAssessment}`)}
                  </p>
                  <p className="break-all font-mono text-xs">
                    SHA256 {d.sha256}
                  </p>
                  <PluginInstallPanel
                    path={`${base}/${encodeURIComponent(version)}`}
                    runtime={d.runtimeKey ?? ''}
                  />
                  <PluginFileBrowser
                    path={`${base}/${encodeURIComponent(version)}`}
                    runtime={d.runtimeKey ?? ''}
                  />
                </article>
              ))}
            </section>
          )}
          <PluginComponentList components={plugin.data.components} />
        </>
      )}
    </div>
  )
}
function PluginInstallPanel({
  path,
  runtime,
}: {
  path: string
  runtime: string
}) {
  const { t } = useTranslation()
  const [requested, setRequested] = useState(false)
  const instructions = usePluginInstructions(path, runtime, requested)
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-4">
        <a
          className="text-sm underline"
          href={withBasePath(
            `${path}/download?runtime=${encodeURIComponent(runtime)}`
          )}
        >
          {t('pluginAccess.downloadArchive')}
        </a>
        <Button variant="outline" onClick={() => setRequested(true)}>
          {t('pluginAccess.generateInstall')}
        </Button>
      </div>
      {instructions.error && (
        <p role="alert" className="text-sm text-destructive">
          {instructions.error.message}
        </p>
      )}
      {instructions.data && (
        <>
          <p className="max-w-prose text-sm">
            {t(`pluginAccess.preparation.${instructions.data.preparation}`)}
          </p>
          {instructions.data.powershellCommand && (
            <p className="text-xs">{t('pluginAccess.posixShell')}</p>
          )}
          <pre className="overflow-auto whitespace-pre-wrap rounded bg-muted p-3 text-sm">
            {instructions.data.command}
          </pre>
          {instructions.data.powershellCommand && (
            <>
              <p className="text-xs">PowerShell</p>
              <pre className="overflow-auto whitespace-pre-wrap rounded bg-muted p-3 text-sm">
                {instructions.data.powershellCommand}
              </pre>
            </>
          )}
          {instructions.data.marketplaceJson && (
            <pre className="overflow-auto whitespace-pre-wrap rounded bg-muted p-3 text-xs">
              {instructions.data.marketplaceJson}
            </pre>
          )}
          <p className="text-xs text-muted-foreground">
            {t('pluginAccess.notRuntimeVerified')}
          </p>
        </>
      )}
    </div>
  )
}
export function PluginFileBrowser({
  path,
  runtime,
}: {
  path: string
  runtime: string
}) {
  const { t } = useTranslation()
  const files = usePluginFiles(path, runtime)
  const [selected, setSelected] = useState('')
  const file = usePluginFile(path, runtime, selected)
  return (
    <details className="space-y-3">
      <summary className="cursor-pointer text-sm">
        {t('pluginAccess.browseFiles')}
      </summary>
      {files.error && (
        <p role="alert" className="text-sm text-destructive">
          {files.error.message}
        </p>
      )}
      <div className="mt-3 grid gap-3 sm:grid-cols-[minmax(0,14rem)_minmax(0,1fr)]">
        <ul className="max-h-72 overflow-auto text-sm">
          {files.data?.files.map((name) => (
            <li key={name}>
              <button
                className="w-full break-all rounded p-2 text-left hover:bg-accent focus-visible:ring-2"
                aria-pressed={selected === name}
                onClick={() => setSelected(name)}
              >
                {name}
              </button>
            </li>
          ))}
        </ul>
        <div>
          {file.error && (
            <p role="alert" className="text-sm text-destructive">
              {file.error.message}
            </p>
          )}
          {file.data && (
            <pre className="max-h-96 overflow-auto whitespace-pre-wrap break-words rounded bg-muted p-3 text-xs">
              {file.data.content}
            </pre>
          )}
        </div>
      </div>
    </details>
  )
}
function PluginComponentList({
  components,
  onUnlink,
}: {
  components: PluginComponent[]
  onUnlink?: (id: number) => void
}) {
  const { t } = useTranslation()
  return (
    <section className="space-y-3 border-t pt-4">
      <h2 className="font-medium">{t('pluginAccess.components')}</h2>
      <p className="max-w-prose text-xs text-muted-foreground">
        {t('pluginAccess.componentIndependence')}
      </p>
      <ul className="space-y-2">
        {components.map((component) => (
          <li
            key={component.id}
            className="flex flex-wrap items-center justify-between gap-2 text-sm"
          >
            <span>
              {component.kind} · {component.displayName} · @
              {component.namespace}/{component.slug}@{component.version}
            </span>
            {onUnlink && (
              <Button variant="outline" onClick={() => onUnlink(component.id)}>
                {t('pluginAccess.unlink')}
              </Button>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
export function PluginProjectGovernance({
  project,
}: {
  project: PluginProject
}) {
  const { t } = useTranslation()
  const change = usePluginGovernance()
  const [reason, setReason] = useState('')
  const [visibility, setVisibility] = useState(project.visibility)
  return (
    <section className="space-y-3 rounded-lg border p-4">
      <h3 className="font-semibold">{t('pluginAccess.governance')}</h3>
      <p className="max-w-prose text-sm text-muted-foreground">
        {t('pluginAccess.externalCopies')}
      </p>
      <label className="block space-y-1 text-sm">
        {t('pluginAccess.reason')}
        <Textarea
          required
          maxLength={10000}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </label>
      <div className="flex flex-wrap gap-2">
        {[
          project.hidden ? 'SHOW' : 'HIDE',
          project.status === 'ARCHIVED' ? 'RESTORE' : 'ARCHIVE',
        ].map((action) => (
          <Button
            key={action}
            variant="outline"
            disabled={change.isPending || !reason.trim()}
            onClick={() =>
              change.mutate({
                action: 'govern',
                id: project.id,
                body: { action, reason },
              })
            }
          >
            {t(`pluginAccess.actions.${action}`)}
          </Button>
        ))}
      </div>
      {project.canManage && (
        <form
          className="flex flex-wrap gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            change.mutate({
              action: 'govern',
              id: project.id,
              body: { action: 'VISIBILITY', visibility, reason },
            })
          }}
        >
          <label className="flex items-center gap-2 text-sm">
            {t('pluginManagement.visibility')}
            <select
              className={selectClass}
              value={visibility}
              onChange={(e) => {
                const v = e.target.value
                if (v === 'PUBLIC' || v === 'PRIVATE' || v === 'NAMESPACE_ONLY')
                  setVisibility(v)
              }}
            >
              {['PRIVATE', 'NAMESPACE_ONLY', 'PUBLIC'].map((v) => (
                <option key={v} value={v}>
                  {t(`pluginManagement.visibilityValues.${v}`)}
                </option>
              ))}
            </select>
          </label>
          <Button
            type="submit"
            variant="outline"
            disabled={change.isPending || !reason.trim()}
          >
            {t('pluginAccess.changeVisibility')}
          </Button>
        </form>
      )}
      {change.error && (
        <p role="alert" className="text-sm text-destructive">
          {change.error.message}
        </p>
      )}
    </section>
  )
}
export function PluginReleaseGovernance({
  release,
}: {
  release: PluginRelease
}) {
  const { t } = useTranslation()
  const change = usePluginGovernance()
  const [reason, setReason] = useState('')
  return (
    <div className="space-y-2 border-t pt-3">
      {release.status === 'PUBLISHED' && (
        <form
          className="space-y-2"
          onSubmit={(e) => {
            e.preventDefault()
            change.mutate({ action: 'yank', id: release.id, body: { reason } })
          }}
        >
          <label className="block space-y-1 text-sm">
            {t('pluginAccess.reason')}
            <Textarea
              required
              maxLength={10000}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <Button
            type="submit"
            variant="destructive"
            disabled={change.isPending || !reason.trim()}
          >
            {t('pluginAccess.yank')}
          </Button>
          <p className="text-xs text-muted-foreground">
            {t('pluginAccess.externalCopies')}
          </p>
        </form>
      )}
      {release.yankReason && (
        <p className="whitespace-pre-wrap text-sm">
          {t('pluginAccess.yankedReason')}: {release.yankReason} (
          {release.yankedBy})
        </p>
      )}
      {change.error && (
        <p role="alert" className="text-sm text-destructive">
          {change.error.message}
        </p>
      )}
    </div>
  )
}
export function PluginComponentEditor({ project }: { project: PluginProject }) {
  const { t } = useTranslation()
  const components = usePluginComponents(project.id)
  const change = usePluginGovernance()
  const [kind, setKind] = useState('SKILL')
  const [namespace, setNamespace] = useState('')
  const [slug, setSlug] = useState('')
  const [version, setVersion] = useState('')
  function link(e: FormEvent) {
    e.preventDefault()
    change.mutate({
      action: 'link',
      id: project.id,
      body: { kind, namespace, slug, version },
    })
  }
  return (
    <div className="space-y-3">
      <PluginComponentList
        components={components.data ?? []}
        onUnlink={
          project.canManage
            ? (id) =>
                change.mutate({ action: 'unlink', id: project.id, linkId: id })
            : undefined
        }
      />
      {project.canManage && (
        <form className="grid gap-3 sm:grid-cols-2" onSubmit={link}>
          <label className="space-y-1 text-sm">
            {t('pluginAccess.componentKind')}
            <select
              className={`${selectClass} w-full`}
              value={kind}
              onChange={(e) => setKind(e.target.value)}
            >
              <option>SKILL</option>
              <option>MCP</option>
            </select>
          </label>
          <label className="space-y-1 text-sm">
            {t('pluginManagement.namespace')}
            <Input
              required
              value={namespace}
              onChange={(e) => setNamespace(e.target.value)}
            />
          </label>
          <label className="space-y-1 text-sm">
            {t('pluginManagement.slug')}
            <Input
              required
              value={slug}
              onChange={(e) => setSlug(e.target.value)}
            />
          </label>
          <label className="space-y-1 text-sm">
            {t('pluginAccess.componentVersion')}
            <Input
              required
              value={version}
              onChange={(e) => setVersion(e.target.value)}
            />
          </label>
          <Button type="submit" variant="outline" disabled={change.isPending}>
            {t('pluginAccess.link')}
          </Button>
        </form>
      )}
      {components.error && <p role="alert">{components.error.message}</p>}
      {change.error && (
        <p role="alert" className="text-sm text-destructive">
          {change.error.message}
        </p>
      )}
    </div>
  )
}
