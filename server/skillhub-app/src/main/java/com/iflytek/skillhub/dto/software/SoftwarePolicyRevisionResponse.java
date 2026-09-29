package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/** One entry of the policy revision history. */
public record SoftwarePolicyRevisionResponse(
        long revision,
        int schemaVersion,
        SoftwarePolicyConfigDto config,
        String changedBy,
        String changeReason,
        Instant createdAt
) {
}
