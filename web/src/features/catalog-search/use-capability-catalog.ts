import { useQuery } from '@tanstack/react-query'
import { catalogApi } from '@/api/client'

export type CapabilityType = 'ALL' | 'SKILL' | 'PLUGIN' | 'MCP'

export function useCapabilityCatalog(type: CapabilityType, query: string) {
  return useQuery({
    queryKey: ['capability-catalog', type, query],
    queryFn: () => catalogApi.search({ type, q: query, page: 0, size: 40 }),
  })
}
