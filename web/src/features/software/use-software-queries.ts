import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { softwareApi } from '@/api/client'
import type {
  SoftwareAppAdminSummary,
  SoftwareInspectionSummary,
  SoftwarePolicyConfigDto,
  SoftwareUploadSessionCreateRequest,
} from '@/api/types'

/**
 * Server-state hooks for the software center: portal catalog, admin catalog
 * and chunked uploads, inspection console, and dynamic policy settings.
 * Mutations invalidate the narrowest matching query family.
 */

const SOFTWARE_KEY = ['software'] as const

// ---- portal ----

export function useSoftwareCategories() {
  return useQuery({
    queryKey: [...SOFTWARE_KEY, 'categories'],
    queryFn: () => softwareApi.listCategories(),
    staleTime: 5 * 60 * 1000,
  })
}

export function usePublishedApps(params: {
  keyword?: string
  categoryCode?: string
  targetOs?: string
  targetArch?: string
  page: number
  size?: number
}) {
  return useQuery({
    queryKey: [...SOFTWARE_KEY, 'catalog', params],
    queryFn: () => softwareApi.listPublishedApps(params),
    placeholderData: keepPreviousData,
  })
}

export function useAppDetail(slug: string | undefined) {
  return useQuery({
    queryKey: [...SOFTWARE_KEY, 'app', slug],
    queryFn: () => softwareApi.getAppDetail(slug ?? ''),
    enabled: !!slug,
  })
}

export function useCreateDownloadSession() {
  return useMutation({
    mutationFn: (packageId: number) => softwareApi.createDownloadSession(packageId),
  })
}

// ---- admin catalog ----

export function useAdminSoftwareApps(params: {
  keyword?: string
  status?: string
  page: number
  size?: number
}) {
  return useQuery({
    queryKey: [...SOFTWARE_KEY, 'admin', 'apps', params],
    queryFn: () => softwareApi.listAdminApps(params),
    placeholderData: keepPreviousData,
  })
}

export function useCreateSoftwareApp() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: { slug: string; name: string; description?: string; categoryCode?: string }) =>
      softwareApi.createApp(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...SOFTWARE_KEY, 'admin', 'apps'] })
    },
  })
}

export function useSetSoftwareAppStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ appId, action }: { appId: number; action: 'publish' | 'takedown' | 'restore' }) =>
      softwareApi.setAppStatus(appId, action),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...SOFTWARE_KEY, 'admin', 'apps'] })
    },
  })
}

export function useSoftwareReleases(appId: number | undefined) {
  return useQuery({
    queryKey: [...SOFTWARE_KEY, 'admin', 'releases', appId],
    queryFn: () => softwareApi.listReleases(appId ?? 0),
    enabled: appId != null,
  })
}

export function useCreateSoftwareRelease() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ appId, displayVersion, releaseNotes }: {
      appId: number
      displayVersion: string
      releaseNotes?: string
    }) => softwareApi.createRelease(appId, { displayVersion, releaseNotes }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...SOFTWARE_KEY, 'admin', 'releases'] })
    },
  })
}

export function useSetPackageStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ packageId, action, rawVersionFields }: {
      packageId: number
      action: 'confirm' | 'publish' | 'takedown' | 'restore'
      rawVersionFields?: Record<string, unknown>
    }) => softwareApi.setPackageStatus(packageId, action, rawVersionFields),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...SOFTWARE_KEY, 'admin'] })
      queryClient.invalidateQueries({ queryKey: [...SOFTWARE_KEY, 'inspections'] })
    },
  })
}

// ---- chunked upload ----

export interface ChunkedUploadCallbacks {
  onProgress?: (uploadedBytes: number, totalBytes: number) => void
}

/**
 * Runs the three-phase chunked transfer for one file: session, sequential
 * chunk PUTs with per-chunk digests, completion. Chunk failures abort the
 * session and release the reservation server-side via cancel.
 */
export async function runChunkedUpload(
  request: SoftwareUploadSessionCreateRequest,
  file: File,
  callbacks?: ChunkedUploadCallbacks,
): Promise<{ packageRevisionId: number | null; sha256: string }> {
  const session = await softwareApi.createUploadSession(request)
  try {
    for (let chunkNo = 1; chunkNo <= session.totalChunks; chunkNo++) {
      const start = (chunkNo - 1) * session.chunkSizeBytes
      const end = Math.min(start + session.chunkSizeBytes, file.size)
      const bytes = await file.slice(start, end).arrayBuffer()
      await softwareApi.putChunk(session.id, chunkNo, bytes, await sha256Hex(bytes))
      callbacks?.onProgress?.(end, file.size)
    }
    const completion = await softwareApi.completeUpload(session.id)
    return { packageRevisionId: completion.packageRevisionId, sha256: completion.sha256 }
  } catch (error) {
    try {
      await softwareApi.cancelUpload(session.id)
    } catch {
      // the cleanup task reclaims abandoned sessions; surface the first error
    }
    throw error
  }
}

export async function sha256Hex(bytes: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

// ---- inspections ----

export function useSoftwareInspections(params: { status?: string; page: number; size?: number }) {
  return useQuery({
    queryKey: [...SOFTWARE_KEY, 'inspections', params],
    queryFn: () => softwareApi.listInspections(params),
    placeholderData: keepPreviousData,
  })
}

export function useSoftwareInspectionDetail(taskId: number | undefined) {
  return useQuery({
    queryKey: [...SOFTWARE_KEY, 'inspections', 'detail', taskId],
    queryFn: () => softwareApi.inspectionDetail(taskId ?? 0),
    enabled: taskId != null,
  })
}

function useInvalidateInspections() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: [...SOFTWARE_KEY, 'inspections'] })
    queryClient.invalidateQueries({ queryKey: [...SOFTWARE_KEY, 'admin'] })
  }
}

export function useRetryInspection() {
  const invalidate = useInvalidateInspections()
  return useMutation({
    mutationFn: (taskId: number) => softwareApi.retryInspection(taskId),
    onSuccess: invalidate,
  })
}

export function useConfirmInspectionMetadata() {
  const invalidate = useInvalidateInspections()
  return useMutation({
    mutationFn: ({ taskId, rawVersionFields }: {
      taskId: number
      rawVersionFields: Record<string, unknown>
    }) => softwareApi.confirmInspectionMetadata(taskId, rawVersionFields),
    onSuccess: invalidate,
  })
}

// ---- dynamic policy settings ----

export function useSoftwareSettings() {
  return useQuery({
    queryKey: [...SOFTWARE_KEY, 'settings'],
    queryFn: () => softwareApi.getSettings(),
  })
}

export function useUpdateSoftwareSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: { expectedRevision: number; changeReason: string; config: SoftwarePolicyConfigDto }) =>
      softwareApi.updateSettings(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...SOFTWARE_KEY, 'settings'] })
    },
  })
}

export function useValidateSoftwareSettings() {
  return useMutation({
    mutationFn: (config: SoftwarePolicyConfigDto) => softwareApi.validateSettings(config),
  })
}

export function useSoftwareSettingsHistory() {
  return useQuery({
    queryKey: [...SOFTWARE_KEY, 'settings', 'history'],
    queryFn: () => softwareApi.settingsHistory(),
  })
}

export function useRollbackSoftwareSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ fromRevision, changeReason }: { fromRevision: number; changeReason: string }) =>
      softwareApi.rollbackSettings(fromRevision, changeReason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [...SOFTWARE_KEY, 'settings'] })
    },
  })
}

export type { SoftwareAppAdminSummary, SoftwareInspectionSummary }
