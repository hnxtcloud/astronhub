import { fetchJson, getCsrfHeaders, namespaceApi } from '@/api/client'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { components } from '@/api/generated/schema'

// Springdoc marks response fields optional; strengthen the fields always populated by the query.
type RequiredGenerated<T, Optional extends keyof T = never> = Required<Omit<T, Optional>> & Pick<T, Optional>
export type PluginDistribution = RequiredGenerated<components['schemas']['PluginDistributionView']>
type PluginReview = RequiredGenerated<components['schemas']['PluginReviewView'], 'testEnvironment'>
type PluginPublication = RequiredGenerated<components['schemas']['PluginPublicationView'], 'lastError' | 'completedAt'>
export type PluginRelease = Omit<RequiredGenerated<components['schemas']['PluginReleaseView'],
  'changelog' | 'publishedAt' | 'publication' | 'yankedAt' | 'yankedBy' | 'yankReason'>, 'distributions' | 'reviews'> & {
  distributions: PluginDistribution[]
  reviews: PluginReview[]
  publication?: PluginPublication | null
}
export type PluginProject = Omit<RequiredGenerated<components['schemas']['PluginProjectView'],
  'summary' | 'latestReleaseId'>, 'releases'> & { releases: PluginRelease[] }
export type PluginProjectInput = components['schemas']['PluginProjectCreateRequest']
export type PluginCommandBody = components['schemas']['PluginProjectUpdateRequest']
  | components['schemas']['PluginReleaseCreateRequest']
  | components['schemas']['PluginDraftUpdateRequest']
  | components['schemas']['PluginReviewRequest']
type PluginProjectPage = Omit<RequiredGenerated<components['schemas']['PageResponsePluginProjectView']>,
  'items'> & { items: PluginProject[] }
const base = '/api/v1/plugin-management'

export function usePluginProjects(page: number, pendingReview: boolean, enabled = true) {
  return useQuery({ queryKey: ['plugin-management', 'projects', page, pendingReview], enabled, queryFn: () =>
    fetchJson<PluginProjectPage>(`${base}/projects?page=${page}&size=20&pendingReview=${pendingReview}`) })
}
export function usePluginProject(id: number | null) {
  return useQuery({ queryKey: ['plugin-management', 'project', id], enabled: id !== null,
    queryFn: () => fetchJson<PluginProject>(`${base}/projects/${id}`),
    refetchInterval: (query) => query.state.data?.releases.some(r =>
      r.publication?.status === 'QUEUED'
      // Task completion can become visible before the release publication transaction.
      || (r.publication?.status === 'SUCCEEDED' && r.status === 'PENDING_REVIEW')) ? 5000 : false })
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
  return useMutation({ mutationFn: (command: { path: string; method?: 'POST' | 'PATCH'; body?: PluginCommandBody; archive?: File }) =>
    fetchJson<PluginProject | PluginRelease>(`${base}${command.path}`, { method: command.method ?? 'POST',
      headers: getCsrfHeaders({ 'Content-Type': command.archive ? 'application/zip' : 'application/json' }),
      body: command.archive ?? JSON.stringify(command.body ?? {}), timeoutMs: 120000,
    }), onSuccess: () => queries.invalidateQueries({ queryKey: ['plugin-management'] }) })
}
