package com.iflytek.skillhub.dto.software;

/**
 * Completion result; retries after a lost response return this same payload
 * without re-creating the revision or re-charging the quota.
 */
public record SoftwareUploadCompleteResponse(
        Long sessionId,
        String status,
        Long packageRevisionId,
        Integer revisionNo,
        String fileName,
        String sha256,
        long byteSize
) {
}
