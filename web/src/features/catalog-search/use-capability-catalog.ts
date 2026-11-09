import { useQuery } from '@tanstack/react-query'
import { catalogApi } from '@/api/client'

export type CapabilityType = 'ALL' | 'SKILL' | 'PLUGIN' | 'MCP' | 'SOFTWARE'

export function useCapabilityCatalog(type: CapabilityType, query: string, page = 0) {
  return useQuery({
    queryKey: ['capability-catalog', type, query, page],
    queryFn: () => catalogApi.search({ type, q: query, page, size: 40 }),
  })
}
