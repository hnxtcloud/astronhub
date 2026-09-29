import { beforeEach, describe, expect, it, vi } from 'vitest'

const softwareApi = vi.hoisted(() => ({
  createUploadSession: vi.fn(),
  putChunk: vi.fn(),
  completeUpload: vi.fn(),
  cancelUpload: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  softwareApi,
}))

import { runChunkedUpload, sha256Hex } from './use-software-queries'

const session = {
  id: 9,
  status: 'OPEN',
  fileName: 'demo.msi',
  format: 'msi',
  targetOs: 'windows',
  targetArch: 'x86_64',
  targetDistro: null,
  byteSize: 24,
  chunkSizeBytes: 10,
  totalChunks: 3,
  uploadedBytes: 0,
  policyRevision: 1,
  storageSessionRef: null,
  createdAt: '2026-01-01T00:00:00Z',
  expiresAt: '2026-01-02T00:00:00Z',
}

describe('runChunkedUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    softwareApi.createUploadSession.mockResolvedValue(session)
    softwareApi.putChunk.mockResolvedValue({
      chunkNo: 1,
      byteSize: 10,
      sha256: 'x',
      alreadyPresent: false,
    })
    softwareApi.completeUpload.mockResolvedValue({
      sessionId: 9,
      status: 'COMPLETED',
      packageRevisionId: 11,
      revisionNo: 1,
      fileName: 'demo.msi',
      sha256: 'x',
      byteSize: 24,
    })
  })

  it('uploads every chunk sequentially with per-chunk digests and completes', async () => {
    const file = new File([new Uint8Array(24).fill(7)], 'demo.msi')
    const progress: Array<[number, number]> = []

    const result = await runChunkedUpload(
      {
        releaseId: 3,
        fileName: 'demo.msi',
        format: 'msi',
        targetOs: 'windows',
        targetArch: 'x86_64',
        targetDistro: null,
        applicabilityNote: null,
        originalFullVersion: '1.0.0',
        byteSize: 24,
      },
      file,
      { onProgress: (uploaded, total) => progress.push([uploaded, total]) },
    )

    expect(softwareApi.createUploadSession).toHaveBeenCalledWith(
      expect.objectContaining({ releaseId: 3, byteSize: 24 }),
    )
    expect(softwareApi.putChunk).toHaveBeenCalledTimes(3)
    expect(softwareApi.putChunk).toHaveBeenNthCalledWith(1, 9, 1, expect.any(ArrayBuffer), expect.any(String))
    // last chunk is the remainder slice (24 = 10 + 10 + 4); args: (id, chunkNo, bytes, digest)
    const lastChunk = softwareApi.putChunk.mock.calls[2][2] as ArrayBuffer
    expect(lastChunk.byteLength).toBe(4)
    expect(progress).toEqual([
      [10, 24],
      [20, 24],
      [24, 24],
    ])
    expect(softwareApi.completeUpload).toHaveBeenCalledWith(9)
    expect(softwareApi.cancelUpload).not.toHaveBeenCalled()
    expect(result).toEqual({ packageRevisionId: 11, sha256: 'x' })
  })

  it('cancels the session and rethrows when a chunk fails', async () => {
    // chunk 1 succeeds, chunk 2 rejects: the failure aborts mid-transfer
    softwareApi.putChunk
      .mockResolvedValueOnce({ chunkNo: 1, byteSize: 10, sha256: 'x', alreadyPresent: false })
      .mockRejectedValue(new Error('chunk 2 rejected'))
    const file = new File([new Uint8Array(24)], 'demo.msi')

    await expect(
      runChunkedUpload(
        {
          releaseId: 3,
          fileName: 'demo.msi',
          format: 'msi',
          targetOs: 'windows',
          targetArch: 'x86_64',
          targetDistro: null,
          applicabilityNote: null,
          originalFullVersion: '1.0.0',
          byteSize: 24,
        },
        file,
      ),
    ).rejects.toThrow('chunk 2 rejected')

    expect(softwareApi.putChunk).toHaveBeenCalledTimes(2)
    expect(softwareApi.completeUpload).not.toHaveBeenCalled()
    expect(softwareApi.cancelUpload).toHaveBeenCalledWith(9)
  })

  it('surfaces the original error even when cancellation also fails', async () => {
    softwareApi.putChunk.mockRejectedValue(new Error('chunk failed'))
    softwareApi.cancelUpload.mockRejectedValue(new Error('session already gone'))
    const file = new File([new Uint8Array(1)], 'demo.msi')

    await expect(
      runChunkedUpload(
        {
          releaseId: 3,
          fileName: 'demo.msi',
          format: 'msi',
          targetOs: 'windows',
          targetArch: 'x86_64',
          targetDistro: null,
          applicabilityNote: null,
          originalFullVersion: '1.0.0',
          byteSize: 1,
        },
        file,
      ),
    ).rejects.toThrow('chunk failed')
  })
})

describe('sha256Hex', () => {
  it('produces the well-known digest for the empty payload', async () => {
    expect(await sha256Hex(new ArrayBuffer(0))).toBe(
      'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    )
  })
})
