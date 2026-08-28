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
    <div className="mx-auto w-full max-w-6xl py-8 md:py-12">
      <div className="max-w-3xl space-y-3">
        <h1 className="text-3xl font-semibold tracking-[-0.025em] text-foreground">{t('catalog.title')}</h1>
        <p className="max-w-[68ch] text-base leading-7 text-muted-foreground">
          {t('catalog.description')}
        </p>
      </div>

      <div className="mt-10 flex flex-col gap-4 border-y py-3 sm:flex-row sm:items-center">
        <label className="flex flex-1 items-center gap-3 rounded-md border bg-white px-3.5 py-2.5 focus-within:border-primary focus-within:ring-1 focus-within:ring-primary/20">
          <Search className="h-4 w-4 text-muted-foreground" />
          <input
            className="w-full bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t('catalog.searchPlaceholder')}
          />
        </label>
        <div className="flex flex-wrap gap-1" aria-label={t('catalog.typeLabel')}>
          {(['ALL', 'SKILL', 'PLUGIN', 'MCP'] as CapabilityType[]).map((candidate) => (
            <button
              key={candidate}
              type="button"
              onClick={() => setType(candidate)}
              className={type === candidate
                ? 'rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground'
                : 'rounded-md px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-secondary/70 hover:text-foreground'}
            >
              {t(`catalog.types.${candidate.toLowerCase()}`)}
            </button>
          ))}
        </div>
      </div>

      {isLoading && <div className="border-b py-12 text-sm text-muted-foreground">{t('catalog.loading')}</div>}
      {isError && <div className="mt-8 rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">{t('catalog.error')}</div>}
      {!isLoading && !isError && data?.items.length === 0 && (
        <div className="mt-8 border-y py-14 text-center text-sm text-muted-foreground">
          {t('catalog.empty')}
        </div>
      )}
      <div className="mt-8 divide-y border-y">
        {data?.items.map((item) => {
          if (item.type === 'PLUGIN') return <PluginCard key={item.coordinate} item={item} />
          if (item.type === 'MCP') return <McpServerCard key={item.coordinate} item={item} />
          return (
            <article key={item.coordinate} className="grid gap-3 py-5 transition-colors hover:bg-secondary/25 sm:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] sm:px-3">
              <div>
                <p className="font-mono text-xs text-muted-foreground">{item.coordinate}</p>
                <h2 className="mt-1 text-base font-semibold text-foreground">{item.displayName}</h2>
              </div>
              <p className="text-sm leading-6 text-muted-foreground">{item.summary || '—'}</p>
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
