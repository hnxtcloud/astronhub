import { fetchJson, getCsrfHeaders, namespaceApi } from '@/api/client'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { components } from '@/api/generated/schema'

// Springdoc marks response fields optional; strengthen the fields always populated by the query.
type RequiredGenerated<T, Optional extends keyof T = never> = Required<Omit<T, Optional>> & Pick<T, Optional>
export type McpVariable = RequiredGenerated<components['schemas']['McpVariableView']>
export type McpProfile = Omit<RequiredGenerated<components['schemas']['McpProfileView']>, 'variables'> & {
  variables: McpVariable[]
}
export type McpReview = RequiredGenerated<components['schemas']['McpReviewDecisionView']>
type McpRevision = Omit<RequiredGenerated<components['schemas']['McpRevisionView'],
  'description' | 'localCuration' | 'publishedAt'>, 'profiles' | 'reviews'> & {
  profiles: McpProfile[]
  reviews: McpReview[]
}
export type McpEntry = Omit<RequiredGenerated<components['schemas']['McpEntryView'],
  'summary' | 'latestRevisionId'>, 'revisions'> & { revisions: McpRevision[] }
export type McpEntryInput = components['schemas']['McpEntryCreateRequest']
export type McpCommandBody = components['schemas']['McpEntryUpdateRequest']
  | components['schemas']['McpRevisionCreateRequest']
  | components['schemas']['McpRevisionUpdateRequest']
  | components['schemas']['McpReviewRequest']
  | components['schemas']['McpRejectRequest']
  | components['schemas']['McpGovernanceRequest']
type McpEntryPage = Omit<RequiredGenerated<components['schemas']['PageResponseMcpEntryView']>,
  'items'> & { items: McpEntry[] }
const base = '/api/v1/mcp-management'

export function useMcpEntries(page: number, pendingReview: boolean, enabled = true) {
  return useQuery({ queryKey: ['mcp-management', 'entries', page, pendingReview], enabled, queryFn: () =>
    fetchJson<McpEntryPage>(`${base}/entries?page=${page}&size=20&pendingReview=${pendingReview}`) })
}
export function useMcpEntry(id: number | null) {
  return useQuery({ queryKey: ['mcp-management', 'entry', id], enabled: id !== null,
    queryFn: () => fetchJson<McpEntry>(`${base}/entries/${id}`) })
}
export function useMcpNamespaces() {
  return useQuery({ queryKey: ['mcp-management', 'namespaces'], queryFn: namespaceApi.listMine })
}
export function useCreateMcpEntry(onCreated: (id: number) => void) {
  const queries = useQueryClient()
  return useMutation({ mutationFn: (input: McpEntryInput) => fetchJson<McpEntry>(`${base}/entries`, {
    method: 'POST', headers: getCsrfHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(input),
  }), onSuccess: (entry) => { onCreated(entry.id); void queries.invalidateQueries({ queryKey: ['mcp-management'] }) } })
}
export function useMcpCommand() {
  const queries = useQueryClient()
  return useMutation({ mutationFn: (command: { path: string; method?: 'POST' | 'PATCH' | 'PUT' | 'DELETE'; body?: McpCommandBody }) =>
    fetchJson<McpEntry | McpRevision>(`${base}${command.path}`, { method: command.method ?? 'POST',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: command.body === undefined ? undefined : JSON.stringify(command.body),
    }), onSuccess: () => queries.invalidateQueries({ queryKey: ['mcp-management'] }) })
}
