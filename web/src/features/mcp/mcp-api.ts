import { useQuery } from '@tanstack/react-query'
import { fetchJson } from '@/api/client'
import type { components } from '@/api/generated/schema'

export type McpConnectionProfile = components['schemas']['McpConnectionProfileResponse']

export function useMcpServerDetail(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['mcp-server-detail', namespace, slug],
    enabled: Boolean(namespace && slug),
    retry: false,
    queryFn: async () => {
      const base = `/api/v1/mcp-servers/${encodeURIComponent(namespace)}/${encodeURIComponent(slug)}`
      const [entry, configuration] = await Promise.all([
        fetchJson<components['schemas']['CapabilityCatalogItem']>(base),
        fetchJson<components['schemas']['McpConnectionProfilesResponse']>(`${base}/connection-profiles`),
      ])
      return { entry, configuration }
    },
  })
}
