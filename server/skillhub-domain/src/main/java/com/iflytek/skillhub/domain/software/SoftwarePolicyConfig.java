package com.iflytek.skillhub.domain.software;

import java.util.List;

/**
 * Typed runtime policy of the software center. Storage values are integer
 * bytes and time values carry an explicit unit field; no implicit unit
 * conversion happens anywhere. Instances are immutable; every change produces
 * a new {@link SoftwarePolicyRevision}.
 */
public record SoftwarePolicyConfig(
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

    public static final int SCHEMA_VERSION = 1;

    /** First-version defaults confirmed by the product decisions. */
    public static SoftwarePolicyConfig defaults() {
        return new SoftwarePolicyConfig(
                5L * 1024 * 1024 * 1024,          // 5 GiB per package
                500L * 1024 * 1024 * 1024,        // 500 GiB total quota
                2,                                 // two concurrent uploads per admin
                8L * 1024 * 1024,                 // 8 MiB chunk
                24L * 60,                         // 24 h upload idle timeout
                24 * 7,                            // 7 days failed temp retention
                5,                                 // cleanup scan every 5 minutes
                1,                                 // one inspection per worker
                15L * 60,                         // 15 min inspection total deadline
                60,                                // 60 s subprocess deadline
                512L * 1024 * 1024,               // 512 MiB soft memory limit
                20L * 1024 * 1024 * 1024,         // 20 GiB extracted bytes budget
                10_000,                            // extracted entries budget
                3,                                 // auto inspection attempts (incl. first)
                24L * 60                          // 24 h download session idle timeout
        );
    }

    /** A single field-level policy validation failure. */
    public record Violation(String field, String messageCode, List<Object> messageArgs) {
        public static Violation of(String field, String messageCode, Object... args) {
            return new Violation(field, messageCode, List.of(args));
        }
    }
}
