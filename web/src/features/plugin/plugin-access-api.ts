import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchJson, getCsrfHeaders } from '@/api/client'
import type { components } from '@/api/generated/schema'
import type { PluginDistribution } from './plugin-api'

export type PublishedPlugin = components['schemas']['PublishedPluginView']
export type PublishedPluginRelease = Omit<
  components['schemas']['PublishedPluginReleaseView'],
  'distributions'
> & { distributions: PluginDistribution[] }
export type PluginFiles = components['schemas']['PluginFileListView']
export type PluginFile = components['schemas']['PluginFileContentView']
export type PluginInstall = components['schemas']['PluginInstallView']
export type PluginComponent = components['schemas']['PluginLinkedComponentView']
type GovernanceInput = components['schemas']['PluginGovernanceRequest']
type LinkInput = components['schemas']['PluginComponentLinkRequest']
const management = '/api/v1/plugin-management'
export const publishedPath = (namespace: string, slug: string) =>
  `/api/v1/plugins/${encodeURIComponent(namespace)}/${encodeURIComponent(slug)}/versions`
export function usePublishedPlugin(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['plugin-published', namespace, slug],
    queryFn: () => fetchJson<PublishedPlugin>(publishedPath(namespace, slug)),
  })
}
export function usePublishedPluginRelease(path: string, version: string) {
  return useQuery({
    queryKey: ['plugin-published-release', path, version],
    enabled: !!version,
    queryFn: () =>
      fetchJson<PublishedPluginRelease>(
        `${path}/${encodeURIComponent(version)}`
      ),
  })
}
export function usePluginFiles(path: string, runtime: string) {
  return useQuery({
    queryKey: ['plugin-files', path, runtime],
    enabled: !!runtime,
    queryFn: () =>
      fetchJson<PluginFiles>(
        `${path}/files?runtime=${encodeURIComponent(runtime)}`
      ),
  })
}
export function usePluginFile(path: string, runtime: string, file: string) {
  return useQuery({
    queryKey: ['plugin-file', path, runtime, file],
    enabled: !!file,
    queryFn: () =>
      fetchJson<PluginFile>(
        `${path}/file?runtime=${encodeURIComponent(runtime)}&path=${encodeURIComponent(file)}`
      ),
  })
}
export function usePluginInstructions(
  path: string,
  runtime: string,
  enabled: boolean
) {
  return useQuery({
    queryKey: ['plugin-install', path, runtime],
    enabled,
    queryFn: () =>
      fetchJson<PluginInstall>(
        `${path}/install?runtime=${encodeURIComponent(runtime)}`
      ),
    staleTime: 0,
  })
}
export function usePluginComponents(id: number) {
  return useQuery({
    queryKey: ['plugin-management', 'components', id],
    queryFn: () =>
      fetchJson<PluginComponent[]>(`${management}/projects/${id}/components`),
  })
}
type Change =
  | { action: 'govern'; id: number; body: GovernanceInput }
  | { action: 'yank'; id: number; body: { reason: string } }
  | { action: 'link'; id: number; body: LinkInput }
  | { action: 'unlink'; id: number; linkId: number }
export function usePluginGovernance() {
  const queries = useQueryClient()
  return useMutation({
    mutationFn: (change: Change) => {
      const path =
        change.action === 'yank'
          ? `/releases/${change.id}/yank`
          : change.action === 'govern'
            ? `/projects/${change.id}/governance`
            : `/projects/${change.id}/components${change.action === 'unlink' ? `/${change.linkId}` : ''}`
      return fetchJson<unknown>(`${management}${path}`, {
        method: change.action === 'unlink' ? 'DELETE' : 'POST',
        headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
        body:
          change.action === 'unlink' ? undefined : JSON.stringify(change.body),
      })
    },
    onSuccess: () => {
      void queries.invalidateQueries({ queryKey: ['plugin-management'] })
      void queries.invalidateQueries({ queryKey: ['plugin-published'] })
    },
  })
}
