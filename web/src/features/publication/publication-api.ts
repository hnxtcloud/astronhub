import { useQuery } from '@tanstack/react-query'
import { ensureCsrfHeaders, fetchJson, softwareApi } from '@/api/client'
import type { components } from '@/api/generated/schema'

export type PublicationDraft = components['schemas']['PublicationDraft']
export type PublicationResult = components['schemas']['PublicationResultView']
export type PublicationContent = components['schemas']['PublicationContentView']
type GeneratedTask = components['schemas']['PublicationTaskView']
export type PublicationTask = Omit<Required<GeneratedTask>, 'draft' | 'progress'> & {
  draft: PublicationDraft
  progress: components['schemas']['PublicationProgressView'] & { results: PublicationResult[]; warnings: string[] }
}
type TaskPage = Omit<Required<components['schemas']['PageResponsePublicationTaskView']>, 'items'> & { items: PublicationTask[] }
type ContentPage = Omit<Required<components['schemas']['PageResponsePublicationContentView']>, 'items'> & { items: PublicationContent[] }
const base = '/api/web/publications'
export const publicationKey = (id?: string) => ['publications', 'task', id] as const

async function write<T>(path: string, method: string, body?: unknown): Promise<T> {
  return fetchJson<T>(base + path, { method, headers: await ensureCsrfHeaders({ 'Content-Type': 'application/json' }),
    body: body === undefined ? undefined : JSON.stringify(body), timeoutMs: 300000 })
}
export const publicationApi = {
  create: () => write<PublicationTask>('', 'POST'),
  get: (id: string) => fetchJson<PublicationTask>(`${base}/${encodeURIComponent(id)}`),
  save: (id: string, revision: number, draft: PublicationDraft) => write<PublicationTask>(`/${encodeURIComponent(id)}`, 'PUT',
    { revision, draft } satisfies components['schemas']['PublicationSaveRequest']),
  material: async (task: PublicationTask, file: File, onProgress?: (task: PublicationTask) => void, stopped?: () => boolean) => {
    const existing = task.progress.materialUpload
    let replace = !!existing && (task.fileName !== file.name || existing.totalBytes !== file.size)
    if (existing && !replace) {
      for (let i = 0; i < (existing.receivedChunks ?? 0); i++) {
        const size = existing.chunkSizeBytes ?? 8 * 1024 * 1024
        const bytes = await file.slice(i * size, (i + 1) * size).arrayBuffer()
        const digest = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))).map(byte => byte.toString(16).padStart(2, '0')).join('')
        if (digest !== existing.chunkDigests?.[i]) { replace = true; break }
      }
    }
    let next = await write<PublicationTask>(`/${encodeURIComponent(task.id)}/material-upload?revision=${task.revision}`, 'POST',
      { fileName: file.name, byteSize: file.size, replace } satisfies components['schemas']['PublicationUploadRequest'])
    onProgress?.(next)
    const upload = next.progress.materialUpload
    if (!upload?.chunkSizeBytes || !upload.totalChunks) throw new Error('Invalid upload session')
    for (let number = (upload.receivedChunks ?? 0) + 1; number <= upload.totalChunks; number++) {
      if (stopped?.()) throw new DOMException('Upload stopped', 'AbortError')
      const offset = (number - 1) * upload.chunkSizeBytes
      next = await fetchJson<PublicationTask>(`${base}/${encodeURIComponent(task.id)}/material-upload/${number}?revision=${next.revision}`,
        { method: 'PUT', headers: await ensureCsrfHeaders({ 'Content-Type': 'application/octet-stream' }),
          body: file.slice(offset, offset + upload.chunkSizeBytes), timeoutMs: 300000 })
      onProgress?.(next)
    }
    if (stopped?.()) throw new DOMException('Upload stopped', 'AbortError')
    next = await write<PublicationTask>(`/${encodeURIComponent(task.id)}/material-upload/complete?revision=${next.revision}`, 'POST')
    onProgress?.(next)
    return next
  },
  command: (task: PublicationTask, command: 'execute' | 'preview' | 'cancel') =>
    write<PublicationTask>(`/${encodeURIComponent(task.id)}/${command}?revision=${task.revision}`, 'POST'),
}
export function usePublicationTask(id?: string) {
  return useQuery({ queryKey: publicationKey(id), enabled: !!id, queryFn: () => publicationApi.get(id!),
    structuralSharing: (previous, incoming) => {
      const known = previous as PublicationTask | undefined
      const received = incoming as PublicationTask
      return known && known.revision > received.revision ? known : received
    },
    refetchInterval: query => ['RUNNING', 'WAITING'].includes(query.state.data?.status ?? '') ? 5000 : false })
}
export function usePublicationTasks(page: number) {
  return useQuery({ queryKey: ['publications', 'tasks', page], queryFn: () => fetchJson<TaskPage>(`${base}?page=${page}`) })
}
export function usePublicationContents(type: string, page: number) {
  return useQuery({ queryKey: ['publications', 'content', type, page],
    queryFn: () => fetchJson<ContentPage>(`${base}/content?type=${encodeURIComponent(type)}&page=${page}`) })
}
export function usePublicationApps(enabled: boolean, keyword: string) {
  return useQuery({ queryKey: ['software', 'publication-targets', keyword], enabled,
    queryFn: () => softwareApi.listAdminApps({ keyword: keyword || undefined, page: 0, size: 100 }) })
}
export function usePublicationSkillTarget(namespace?: string, slug?: string, enabled = false) {
  return useQuery({ queryKey: ['publications', 'skill-target', namespace, slug], enabled: enabled && !!namespace && !!slug,
    queryFn: () => fetchJson<PublicationContent | null>(`${base}/skill-target?namespace=${encodeURIComponent(namespace!)}&slug=${encodeURIComponent(slug!)}`) })
}
export function usePublicationRegistries(enabled: boolean) {
  return useQuery({ queryKey: ['plugin-npm', 'registries'], enabled,
    queryFn: () => fetchJson<components['schemas']['RegistryView'][]>('/api/v1/plugin-management/npm/registries') })
}
