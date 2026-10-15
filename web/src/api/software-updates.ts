import type { components } from './generated/schema'
import { ensureCsrfHeaders, fetchJson } from './client'

export type UpdateAsset = Required<components['schemas']['SoftwareReleaseAssetResponse']>
export type UpdateRelease = Omit<Required<components['schemas']['SoftwareUpdateReleaseResponse']>, 'assets'> & { assets: UpdateAsset[] }
export type UpdateCreate = components['schemas']['SoftwareUpdateCreateRequest']
export type UpdateChannel = Required<components['schemas']['SoftwareUpdateChannelResponse']>
export type UpdateSource = Omit<Required<components['schemas']['SoftwareUpdateSourceResponse']>, 'channels'> & { channels: UpdateChannel[] }
export type UpdateChannelInput = components['schemas']['SoftwareUpdateChannelRequest']
export type SoftwarePublisher = Required<components['schemas']['SoftwarePublisherResponse']>
export type SoftwarePublisherCreated = Required<components['schemas']['SoftwarePublisherCreatedResponse']>

const base = '/api/v1/admin/software'
async function write<T>(path: string, body?: unknown, method = 'POST'): Promise<T> {
  return fetchJson<T>(`${base}${path}`, { method, headers: await ensureCsrfHeaders({ 'Content-Type': 'application/json' }), body: body === undefined ? undefined : JSON.stringify(body) })
}
export const softwareUpdates = {
  list: (catalogReleaseId: number) => fetchJson<UpdateRelease[]>(`${base}/update-releases?catalogReleaseId=${catalogReleaseId}`),
  published: (applicationId: number) => fetchJson<UpdateRelease[]>(`${base}/update-releases?applicationId=${applicationId}`),
  create: (body: UpdateCreate) => write<UpdateRelease>('/update-releases', body),
  link: (id: number, packageRevisionId: number, relativePath: string) => write<UpdateAsset>(`/update-releases/${id}/package-assets`, { packageRevisionId, relativePath }),
  upload: async (id: number, path: string, role: string, file: File) => fetchJson<UpdateAsset>(`${base}/update-releases/${id}/assets?${new URLSearchParams({ path, role })}`, {
    method: 'POST', headers: await ensureCsrfHeaders({ 'Content-Type': 'application/octet-stream' }), body: file,
    timeoutMs: 30 * 60 * 1000,
  }),
  remove: (id: number, assetId: number) => write<void>(`/update-releases/${id}/assets/${assetId}`, undefined, 'DELETE'),
  action: (id: number, action: 'validate' | 'publish' | 'withdraw') => write<unknown>(`/update-releases/${id}/${action}`),
  source: (appId: number) => fetchJson<UpdateSource>(`${base}/apps/${appId}/update-source`),
  anonymous: (appId: number, anonymousEnabled: boolean) => write<UpdateSource>(`/apps/${appId}/update-source`, { anonymousEnabled }, 'PUT'),
  channel: (appId: number, body: UpdateChannelInput) => write<UpdateChannel>(`/apps/${appId}/update-channels`, body, 'PUT'),
}

export const softwarePublishers = {
  list: (appId: number) => fetchJson<SoftwarePublisher[]>(`${base}/apps/${appId}/publishers`),
  create: (appId: number, body: components['schemas']['SoftwarePublisherCreateRequest']) => write<SoftwarePublisherCreated>(`/apps/${appId}/publishers`, body),
  revoke: (appId: number, tokenId: number) => write<void>(`/apps/${appId}/publishers/${tokenId}`, undefined, 'DELETE'),
}
