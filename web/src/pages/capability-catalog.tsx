import { useEffect, useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { McpServerCard } from '@/entities/mcp-server/mcp-server-card'
import { PluginCard } from '@/entities/plugin/plugin-card'
import { useCapabilityCatalog, type CapabilityType } from '@/features/catalog-search/use-capability-catalog'
import { Pagination } from '@/shared/components/pagination'
import { withBasePath } from '@/shared/lib/base-path'

function CapabilityCatalogPage({ initialType = 'ALL' }: { initialType?: CapabilityType }) {
  const { t } = useTranslation()
  const search = useSearch({ strict: false })
  const navigate = useNavigate()
  const validTypes: CapabilityType[] = ['ALL', 'SKILL', 'PLUGIN', 'MCP', 'SOFTWARE']
  const searchType = validTypes.includes(search.type as CapabilityType) ? search.type as CapabilityType : initialType
  const [type, setType] = useState<CapabilityType>(searchType)
  const [query, setQuery] = useState(search.q ?? '')
  const [page, setPage] = useState(Number(search.page) || 0)
  const { data, isLoading, isError } = useCapabilityCatalog(type, query, page)
  useEffect(() => { setType(searchType); setQuery(search.q ?? ''); setPage(Number(search.page) || 0) }, [searchType, search.q, search.page])
  function update(nextType: CapabilityType, nextQuery: string, nextPage = 0) {
    setType(nextType); setQuery(nextQuery); setPage(nextPage)
    void navigate({ to: '/catalog', search: { type: nextType, q: nextQuery || undefined, page: nextPage } })
  }

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
            type="search"
            aria-label={t('catalog.searchPlaceholder')}
            className="w-full bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"
            value={query}
            onChange={(event) => update(type, event.target.value)}
            placeholder={t('catalog.searchPlaceholder')}
          />
        </label>
        <div className="flex flex-wrap gap-1" aria-label={t('catalog.typeLabel')}>
          {validTypes.map((candidate) => (
            <button
              key={candidate}
              type="button"
              aria-pressed={type === candidate}
              onClick={() => update(candidate, query)}
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
          const detail = item.type === 'SOFTWARE' ? `/software/apps/${encodeURIComponent(item.slug)}`
            : `/space/${encodeURIComponent(item.namespace)}/${encodeURIComponent(item.slug)}`
          return (
            <article key={item.coordinate} className="grid gap-3 py-5 transition-colors hover:bg-secondary/25 sm:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] sm:px-3">
              <div>
                <h2 className="text-base font-semibold text-foreground"><a href={withBasePath(detail)} className="rounded-sm hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">{item.displayName}</a></h2>
                <p className="mt-1 truncate font-mono text-xs text-muted-foreground" title={item.coordinate}>{item.coordinate}</p>
              </div>
              <p className="text-sm leading-6 text-muted-foreground">{item.summary || '—'}</p>
            </article>
          )
        })}
      </div>
      {(data?.total ?? 0) > 40 && <Pagination page={page} totalPages={Math.ceil((data?.total ?? 0) / 40)} onPageChange={value => update(type, query, value)} />}
    </div>
  )
}

export function CatalogPage() { return <CapabilityCatalogPage /> }
export function PluginCatalogPage() { return <CapabilityCatalogPage initialType="PLUGIN" /> }
export function McpCatalogPage() { return <CapabilityCatalogPage initialType="MCP" /> }
