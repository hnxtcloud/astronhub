package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/**
 * The active policy revision plus the deployment-declared hard limits the
 * values must stay within. Limits are read-only context, not editable config.
 */
public record SoftwareSettingsResponse(
        long revision,
        int schemaVersion,
        SoftwarePolicyConfigDto config,
        SoftwareDeploymentLimitsDto deploymentLimits,
        String changedBy,
        Instant changedAt
) {
}
