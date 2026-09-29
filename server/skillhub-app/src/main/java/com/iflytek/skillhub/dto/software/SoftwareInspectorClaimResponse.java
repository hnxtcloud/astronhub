package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/**
 * A task handed to an inspector worker by the internal claim API, including
 * the claim-time policy snapshot the attempt is judged against.
 */
public record SoftwareInspectorClaimResponse(
        boolean claimed,
        Long taskId,
        Long packageRevisionId,
        Integer attemptNo,
        String attemptToken,
        String objectKey,
        String format,
        Long policyRevision,
        Long totalTimeoutSeconds,
        Long subprocessTimeoutSeconds,
        Long softMemoryLimitBytes,
        Long maxExtractedBytes,
        Integer maxExtractedEntries,
        Instant leaseExpiresAt
) {
    public static SoftwareInspectorClaimResponse empty() {
        return new SoftwareInspectorClaimResponse(false, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
