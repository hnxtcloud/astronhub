package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/** Admin-list projection of one inspection task with its package facts. */
public record SoftwareInspectionSummaryResponse(
        Long taskId,
        Long packageRevisionId,
        Long applicationId,
        String status,
        Integer attemptCount,
        Integer maxAttempts,
        Integer manualRetryCount,
        Long policyRevision,
        String format,
        String fileName,
        String originalFullVersion,
        Long byteSize,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant nextAttemptNotBefore,
        Instant createdAt,
        Instant updatedAt
) {
}
