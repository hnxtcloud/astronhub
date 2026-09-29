package com.iflytek.skillhub.dto.software;

/**
 * Deployment-declared constraints surfaced to administrators: editable policy
 * values must stay within these; changing them is an operations task.
 */
public record SoftwareDeploymentLimitsDto(
        long proxyMaxBodyBytes,
        long workerMemoryHardLimitBytes,
        long workerMemoryReservedBytes,
        long workerAvailableMemoryBytes
) {
}
