package com.iflytek.skillhub.domain.software;

/**
 * Deployment-declared hard limits that policy values must stay within. These
 * caps come from the deployment (proxy request body, worker container memory),
 * not from the editable policy; the settings API only shows and enforces them.
 */
public record SoftwarePolicyDeploymentLimits(
        long proxyMaxBodyBytes,
        long workerMemoryHardLimitBytes,
        long workerMemoryReservedBytes
) {

    public static SoftwarePolicyDeploymentLimits defaults() {
        return new SoftwarePolicyDeploymentLimits(
                100L * 1024 * 1024,          // ~100 MB proxy/servlet request cap
                1024L * 1024 * 1024,         // 1 GiB worker container hard limit
                512L * 1024 * 1024           // container overhead reserved for the worker JVM itself
        );
    }

    public long workerAvailableMemoryBytes() {
        return Math.max(0, workerMemoryHardLimitBytes - workerMemoryReservedBytes);
    }
}
