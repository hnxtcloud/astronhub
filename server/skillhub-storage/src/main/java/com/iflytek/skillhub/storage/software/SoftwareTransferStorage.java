package com.iflytek.skillhub.storage.software;

import java.io.InputStream;
import java.util.List;

/**
 * Transfer capability for the software center: chunked upload sessions and
 * range reads, independent of the legacy skill-package operations on
 * {@link com.iflytek.skillhub.storage.ObjectStorageService}. Implementations
 * compute digests from the actual bytes they receive; client-declared
 * checksums are advisory only. Staged chunks are two-phase (stage, then
 * commit/discard) so a conflicting retransmit never replaces stored bytes.
 */
public interface SoftwareTransferStorage {

    /** Creates a provider upload session and returns its opaque reference. */
    String initiateUpload(String objectKey);

    /**
     * Streams {@code data} (exactly {@code declaredLength} bytes) into a
     * staging area, computing the length and SHA-256 from the bytes received.
     * The staged bytes are not part of the session until {@link #commitChunk}.
     */
    StagedChunk stageChunk(String storageSessionId, int chunkNo, InputStream data, long declaredLength);

    /** Promotes the staged chunk; replaces same-numbered bytes only after digest match. */
    void commitChunk(String storageSessionId, String objectKey, int chunkNo);

    /** Drops the staged chunk, keeping any previously committed bytes intact. */
    void discardChunk(String storageSessionId, int chunkNo);

    /**
     * Merges the committed chunks in order into the final immutable object and
     * computes its SHA-256 from the full byte stream (bounded buffer; never the
     * whole file in memory). Multipart providers recover part identifiers from
     * the provider session, not from caller-supplied etags.
     */
    CompleteResult completeUpload(String storageSessionId, String objectKey, List<Integer> orderedChunkNos);

    /** Aborts the provider upload session and discards all session temp bytes. */
    void abortUpload(String storageSessionId, String objectKey);

    /** Exact byte size of a stored object. */
    long objectSize(String objectKey);

    /**
     * Bounded range read of {@code length} bytes starting at {@code offset};
     * callers close the stream. Implementations never return an
     * unbounded/whole-object stream here.
     */
    InputStream readRange(String objectKey, long offset, long length);

    void deleteObject(String objectKey);

    /** Length and digest of a staged (not yet committed) chunk. */
    record StagedChunk(long byteSize, String sha256Hex) {
    }

    /** Size and full-stream SHA-256 of the completed object. */
    record CompleteResult(long byteSize, String sha256Hex) {
    }
}
