package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/** Snapshot of an upload session after creation or from the progress view. */
public record SoftwareUploadSessionResponse(
        Long id,
        String status,
        String fileName,
        String format,
        String targetOs,
        String targetArch,
        String targetDistro,
        long expectedByteSize,
        long chunkSizeBytes,
        int totalChunks,
        long uploadedBytes,
        long policyRevision,
        Instant lastActivityAt,
        Instant expiresAt
) {
}
