import { fetchJson, getCsrfHeaders, namespaceApi } from '@/api/client'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

// Transitional contracts pending the integration branch's generated OpenAPI export.
// Keep them in this feature until generated schemas become available; never edit schema.d.ts.
export interface PluginDistribution {
  id: number; runtimeKey: string; nativeName: string; nativeVersion: string; sha256: string
  compatibilityEvidence: string; securityAssessment: string; inventoryJson: string; manifestJson: string
}
export interface PluginRelease {
  id: number; projectId: number; version: string; status: string; changelog?: string | null
  distributions: PluginDistribution[]
  reviews: Array<{ reviewerId: string; decision: string; notes: string; runtimeTestResult: string; testEnvironment?: string | null }>
  publication?: { id: number; status: string; requiredChannels: string; completedChannels: string; lastError?: string | null; attemptCount: number } | null
}
export interface PluginProject {
  id: number; namespaceId: number; namespace: string; slug: string; displayName: string; summary?: string | null
  visibility: string; status: string; canManage: boolean; canReview: boolean; releases: PluginRelease[]
}
export interface PluginProjectInput {
  namespaceId: number; slug: string; displayName: string; summary: string; visibility: string
}
const base = '/api/v1/plugin-management'

export function usePluginProjects(page: number, pendingReview: boolean) {
  return useQuery({ queryKey: ['plugin-management', 'projects', page, pendingReview], queryFn: () =>
    fetchJson<{ items: PluginProject[]; total: number; page: number; size: number }>(`${base}/projects?page=${page}&size=20&pendingReview=${pendingReview}`) })
}
export function usePluginProject(id: number | null) {
  return useQuery({ queryKey: ['plugin-management', 'project', id], enabled: id !== null,
    queryFn: () => fetchJson<PluginProject>(`${base}/projects/${id}`),
    refetchInterval: (query) => query.state.data?.releases.some(r => r.publication?.status === 'QUEUED') ? 5000 : false })
}
export function usePluginNamespaces() {
  return useQuery({ queryKey: ['plugin-management', 'namespaces'], queryFn: namespaceApi.listMine })
}
export function useCreatePluginProject(onCreated: (id: number) => void) {
  const queries = useQueryClient()
  return useMutation({ mutationFn: (input: PluginProjectInput) => fetchJson<PluginProject>(`${base}/projects`, {
    method: 'POST', headers: getCsrfHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(input),
  }), onSuccess: (project) => { onCreated(project.id); void queries.invalidateQueries({ queryKey: ['plugin-management'] }) } })
}
export function usePluginCommand() {
  const queries = useQueryClient()
  return useMutation({ mutationFn: (command: { path: string; method?: 'POST' | 'PATCH'; body?: Record<string, unknown>; archive?: File }) =>
    fetchJson<unknown>(`${base}${command.path}`, { method: command.method ?? 'POST',
      headers: getCsrfHeaders({ 'Content-Type': command.archive ? 'application/zip' : 'application/json' }),
      body: command.archive ?? JSON.stringify(command.body ?? {}), timeoutMs: 120000,
    }), onSuccess: () => queries.invalidateQueries({ queryKey: ['plugin-management'] }) })
}
