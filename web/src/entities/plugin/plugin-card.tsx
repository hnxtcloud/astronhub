import { Boxes, Download } from 'lucide-react'
import type { CapabilityCatalogItem } from '@/api/types'

export function PluginCard({ item }: { item: CapabilityCatalogItem }) {
  return (
    <article className="rounded-2xl border bg-card p-6 shadow-sm transition-shadow hover:shadow-md">
      <div className="mb-4 flex items-start justify-between gap-4">
        <div>
          <div className="mb-2 flex items-center gap-2 text-sm text-muted-foreground">
            <Boxes className="h-4 w-4" />
            <span>{item.coordinate}</span>
          </div>
          <h2 className="text-xl font-semibold text-foreground">{item.displayName}</h2>
        </div>
        {item.version && <span className="rounded-full bg-muted px-3 py-1 text-xs">v{item.version}</span>}
      </div>
      <p className="min-h-12 text-sm leading-6 text-muted-foreground">
        {item.summary || '—'}
      </p>
      <div className="mt-5 flex flex-wrap items-center gap-2">
        {item.targets.map((target) => (
          <span key={target} className="rounded-full border px-2.5 py-1 text-xs text-muted-foreground">
            {target}
          </span>
        ))}
        <span className="ml-auto flex items-center gap-1 text-xs text-muted-foreground">
          <Download className="h-3.5 w-3.5" /> {item.primaryMetric}
        </span>
      </div>
    </article>
  )
}
