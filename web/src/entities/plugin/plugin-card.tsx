import { Boxes, Download } from 'lucide-react'
import type { CapabilityCatalogItem } from '@/api/types'

export function PluginCard({ item }: { item: CapabilityCatalogItem }) {
  return (
    <article className="grid gap-4 py-5 transition-colors hover:bg-secondary/25 sm:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_auto] sm:items-center sm:px-3">
      <div className="flex items-start justify-between gap-4 sm:block">
        <div>
          <div className="mb-1.5 flex items-center gap-2 font-mono text-xs text-muted-foreground">
            <Boxes className="h-4 w-4" />
            <span>{item.coordinate}</span>
          </div>
          <h2 className="text-base font-semibold text-foreground">{item.displayName}</h2>
        </div>
        {item.version && <span className="rounded-md bg-muted px-2 py-1 font-mono text-xs sm:hidden">v{item.version}</span>}
      </div>
      <p className="text-sm leading-6 text-muted-foreground">
        {item.summary || '—'}
      </p>
      <div className="flex min-w-32 flex-wrap items-center gap-2 sm:justify-end">
        {item.version && <span className="hidden rounded-md bg-muted px-2 py-1 font-mono text-xs sm:inline">v{item.version}</span>}
        <span className="flex items-center gap-1 text-xs text-muted-foreground">
          <Download className="h-3.5 w-3.5" /> {item.primaryMetric}
        </span>
      </div>
      {item.targets.length > 0 && (
        <div className="flex flex-wrap gap-1.5 sm:col-start-2 sm:col-end-4">
          {item.targets.map((target) => (
            <span key={target} className="rounded-md bg-secondary px-2 py-1 text-xs text-secondary-foreground">
              {target}
            </span>
          ))}
        </div>
      )}
    </article>
  )
}
