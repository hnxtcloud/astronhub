import { useState } from 'react'
import { Link, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Copy } from 'lucide-react'
import { ApiError } from '@/api/client'
import { useMcpServerDetail, type McpConnectionProfile } from '@/features/mcp/mcp-api'
import { buildMcpClientConfiguration } from '@/features/mcp/connection-configuration'
import { copyToClipboard } from '@/shared/lib/clipboard'
import { Button } from '@/shared/ui/button'

export function McpServerDetailPage() {
  const { namespace = '', slug = '' } = useParams({ strict: false })
  const { t } = useTranslation()
  const query = useMcpServerDetail(namespace, slug)
  const unavailable = query.error instanceof ApiError && [403, 404].includes(query.error.status)

  return (
    <div className="mx-auto max-w-5xl space-y-8 py-8 md:py-12">
      <Link to="/mcp-servers" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" aria-hidden />{t('mcpDetail.back')}
      </Link>
      {query.isLoading && <p role="status">{t('common.loading')}</p>}
      {query.isError && (
        <div role="alert" className="space-y-3 rounded-md border p-5">
          <h1 className="text-xl font-semibold">{t(unavailable ? 'mcpDetail.unavailable' : 'mcpDetail.error')}</h1>
          {!unavailable && <Button onClick={() => void query.refetch()}>{t('mcpDetail.retry')}</Button>}
        </div>
      )}
      {!query.isError && query.data && (
        <>
          <header className="space-y-3">
            <p className="break-all font-mono text-sm text-muted-foreground">{query.data.entry.coordinate}</p>
            <h1 className="text-3xl font-semibold tracking-tight">{query.data.entry.displayName}</h1>
            <p className="max-w-[70ch] whitespace-pre-wrap leading-7 text-muted-foreground">{query.data.entry.summary}</p>
            <p className="text-sm text-muted-foreground">{t('mcpDetail.revision', { revision: query.data.configuration.revision })}</p>
          </header>
          {query.data.configuration.description && <p className="max-w-[70ch] whitespace-pre-wrap leading-7">{query.data.configuration.description}</p>}
          <section className="space-y-5" aria-labelledby="mcp-profiles-title">
            <h2 id="mcp-profiles-title" className="text-xl font-semibold">{t('mcpDetail.profiles')}</h2>
            <details className="text-sm text-muted-foreground">
              <summary className="cursor-pointer">{t('common.help')}</summary>
              <p className="mt-2 max-w-[70ch] leading-6">{t('mcpDetail.templateHint')}</p>
            </details>
            {query.data.configuration.profiles?.length === 0 && <p>{t('mcpDetail.empty')}</p>}
            <div className="divide-y border-y">
              {query.data.configuration.profiles?.map(profile => (
                <ConnectionProfile key={`${query.data.configuration.revision}-${profile.profileKey}`} profile={profile} serverName={slug} />
              ))}
            </div>
          </section>
        </>
      )}
    </div>
  )
}

function ConnectionProfile({ profile, serverName }: { profile: McpConnectionProfile; serverName: string }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const [copyError, setCopyError] = useState(false)
  const configuration = buildMcpClientConfiguration(profile, serverName)
  async function copy() {
    if (!configuration) return
    setCopyError(false)
    try { await copyToClipboard(configuration); setCopied(true) }
    catch { setCopyError(true) }
  }

  return (
    <article className="space-y-5 py-6" aria-label={profile.displayName ?? profile.profileKey}>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <h3 className="text-lg font-semibold">{profile.displayName ?? profile.profileKey}</h3>
          <p className="text-sm text-muted-foreground">{t(`mcpDetail.transports.${profile.transportType}`)}</p>
        </div>
        <div className="flex items-center gap-3">
          <span role="status" className="text-sm text-muted-foreground">{copied ? t('mcpDetail.copied') : ''}</span>
          <Button variant="outline" onClick={() => void copy()} disabled={!configuration}>
            <Copy className="h-4 w-4" aria-hidden />{t('mcpDetail.copy')}
          </Button>
        </div>
      </div>
      {copyError && <p role="alert" className="text-sm text-destructive">{t('mcpDetail.copyError')}</p>}
      {!configuration && <p className="text-sm text-muted-foreground">{t('mcpDetail.manualConfiguration')}</p>}
      <pre className="max-w-full overflow-auto rounded-md bg-muted p-4 text-sm leading-6" aria-label={t('mcpDetail.configuration')}><code>{configuration ?? JSON.stringify({
        transport: profile.transportType, endpoint: profile.endpointTemplate, command: profile.commandTemplate, arguments: profile.arguments,
      }, null, 2)}</code></pre>
      {(profile.variables?.length ?? 0) > 0 && (
        <div className="space-y-3">
          <h4 className="font-medium">{t('mcpDetail.variables')}</h4>
          <dl className="space-y-4 text-sm">
            {profile.variables?.map(variable => (
              <div key={variable.name} className="space-y-1">
                <dt className="flex flex-wrap items-center gap-3">
                  <code>{variable.name}</code>
                  <span className="text-muted-foreground">{t(variable.required ? 'mcpDetail.required' : 'mcpDetail.optional')}</span>
                  {variable.secret && <span className="text-muted-foreground">{t('mcpDetail.secret')}</span>}
                </dt>
                <dd className="max-w-[70ch] space-y-1 text-muted-foreground">
                  {variable.description && <p>{variable.description}</p>}
                  {!variable.secret && variable.defaultValue != null && <p>{t('mcpDetail.defaultValue', { value: variable.defaultValue })}</p>}
                  {variable.validationHint && <p>{variable.validationHint}</p>}
                </dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </article>
  )
}
