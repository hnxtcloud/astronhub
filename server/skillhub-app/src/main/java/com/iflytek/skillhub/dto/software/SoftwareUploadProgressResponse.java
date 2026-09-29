package com.iflytek.skillhub.dto.software;

import java.util.List;

/** Uploaded-chunk manifest for cross-page resume revalidation. */
public record SoftwareUploadProgressResponse(
        Long sessionId,
        String status,
        int totalChunks,
        long chunkSizeBytes,
        long uploadedBytes,
        List<UploadedChunk> chunks,
        java.time.Instant expiresAt
) {

    public record UploadedChunk(int chunkNo, long byteSize, String sha256) {
    }
}
