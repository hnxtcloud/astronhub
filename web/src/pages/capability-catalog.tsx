import { useState } from 'react'
import { Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { McpServerCard } from '@/entities/mcp-server/mcp-server-card'
import { PluginCard } from '@/entities/plugin/plugin-card'
import { useCapabilityCatalog, type CapabilityType } from '@/features/catalog-search/use-capability-catalog'

function CapabilityCatalogPage({ initialType = 'ALL' }: { initialType?: CapabilityType }) {
  const { t } = useTranslation()
  const [type, setType] = useState<CapabilityType>(initialType)
  const [query, setQuery] = useState('')
  const { data, isLoading, isError } = useCapabilityCatalog(type, query)

  return (
    <div className="space-y-8 py-8">
      <div className="space-y-3">
        <p className="text-sm font-medium text-primary">{t('catalog.eyebrow')}</p>
        <h1 className="text-4xl font-bold tracking-tight">{t('catalog.title')}</h1>
        <p className="max-w-2xl text-muted-foreground">
          {t('catalog.description')}
        </p>
      </div>

      <div className="flex flex-col gap-4 rounded-2xl border bg-card p-4 sm:flex-row sm:items-center">
        <label className="flex flex-1 items-center gap-3 rounded-xl border bg-background px-4 py-3">
          <Search className="h-4 w-4 text-muted-foreground" />
          <input
            className="w-full bg-transparent text-sm outline-none"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t('catalog.searchPlaceholder')}
          />
        </label>
        <div className="flex flex-wrap gap-2" aria-label={t('catalog.typeLabel')}>
          {(['ALL', 'SKILL', 'PLUGIN', 'MCP'] as CapabilityType[]).map((candidate) => (
            <button
              key={candidate}
              type="button"
              onClick={() => setType(candidate)}
              className={type === candidate
                ? 'rounded-full bg-brand-gradient px-4 py-2 text-sm text-white'
                : 'rounded-full border px-4 py-2 text-sm text-muted-foreground hover:bg-muted'}
            >
              {t(`catalog.types.${candidate.toLowerCase()}`)}
            </button>
          ))}
        </div>
      </div>

      {isLoading && <div className="py-16 text-center text-muted-foreground">{t('catalog.loading')}</div>}
      {isError && <div className="rounded-xl border border-destructive/30 p-6 text-destructive">{t('catalog.error')}</div>}
      {!isLoading && !isError && data?.items.length === 0 && (
        <div className="rounded-2xl border border-dashed py-20 text-center text-muted-foreground">
          {t('catalog.empty')}
        </div>
      )}
      <div className="grid gap-5 md:grid-cols-2">
        {data?.items.map((item) => {
          if (item.type === 'PLUGIN') return <PluginCard key={item.coordinate} item={item} />
          if (item.type === 'MCP') return <McpServerCard key={item.coordinate} item={item} />
          return (
            <article key={item.coordinate} className="rounded-2xl border bg-card p-6 shadow-sm">
              <p className="mb-2 text-sm text-muted-foreground">{item.coordinate}</p>
              <h2 className="text-xl font-semibold">{item.displayName}</h2>
              <p className="mt-3 text-sm leading-6 text-muted-foreground">{item.summary}</p>
            </article>
          )
        })}
      </div>
    </div>
  )
}

export function CatalogPage() { return <CapabilityCatalogPage /> }
export function PluginCatalogPage() { return <CapabilityCatalogPage initialType="PLUGIN" /> }
export function McpCatalogPage() { return <CapabilityCatalogPage initialType="MCP" /> }
