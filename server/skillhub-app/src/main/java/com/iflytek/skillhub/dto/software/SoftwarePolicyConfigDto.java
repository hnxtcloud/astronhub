package com.iflytek.skillhub.dto.software;

/** Typed software-center policy values as exposed over the settings API. */
public record SoftwarePolicyConfigDto(
        long maxPackageBytes,
        long totalQuotaBytes,
        int maxActiveUploadsPerAdmin,
        long chunkSizeBytes,
        long uploadIdleTimeoutMinutes,
        long failedTempRetentionHours,
        long cleanupScanIntervalMinutes,
        int maxConcurrentInspectionsPerWorker,
        long inspectionTotalTimeoutSeconds,
        long inspectionSubprocessTimeoutSeconds,
        long inspectionSoftMemoryLimitBytes,
        long inspectionMaxExtractedBytes,
        int inspectionMaxExtractedEntries,
        int maxAutoInspectionAttempts,
        long downloadSessionIdleTimeoutMinutes
) {
}
