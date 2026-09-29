package com.iflytek.skillhub.domain.software;

import java.time.Instant;

/**
 * The currently active policy revision with its typed configuration. Tasks
 * and admission checks capture this snapshot; later revisions never mutate an
 * already-captured snapshot.
 */
public record SoftwarePolicySnapshot(
        long revision,
        int schemaVersion,
        SoftwarePolicyConfig config,
        String changedBy,
        Instant changedAt
) {
}
