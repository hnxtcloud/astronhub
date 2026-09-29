package com.iflytek.skillhub.domain.software;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates typed policy configurations against field bounds, cross-field
 * rules, and deployment-declared hard limits. Range checks here are the single
 * authority; the settings API and upload/inspection admissions rely on them.
 */
public final class SoftwarePolicyValidator {

    private static final long MIB = 1024L * 1024;
    private static final long S3_MIN_PART_SIZE = 5 * MIB;
    private static final int S3_MAX_PART_COUNT = 10_000;

    private SoftwarePolicyValidator() {
    }

    public static List<SoftwarePolicyConfig.Violation> validate(SoftwarePolicyConfig config,
                                                                SoftwarePolicyDeploymentLimits limits) {
        List<SoftwarePolicyConfig.Violation> violations = new ArrayList<>();

        requirePositive(violations, "maxPackageBytes", config.maxPackageBytes());
        requirePositive(violations, "totalQuotaBytes", config.totalQuotaBytes());
        requirePositive(violations, "uploadIdleTimeoutMinutes", config.uploadIdleTimeoutMinutes());
        requirePositive(violations, "failedTempRetentionHours", config.failedTempRetentionHours());
        requirePositive(violations, "cleanupScanIntervalMinutes", config.cleanupScanIntervalMinutes());
        requirePositive(violations, "inspectionTotalTimeoutSeconds", config.inspectionTotalTimeoutSeconds());
        requirePositive(violations, "inspectionSubprocessTimeoutSeconds",
                config.inspectionSubprocessTimeoutSeconds());
        requirePositive(violations, "inspectionSoftMemoryLimitBytes", config.inspectionSoftMemoryLimitBytes());
        requirePositive(violations, "inspectionMaxExtractedBytes", config.inspectionMaxExtractedBytes());
        requirePositive(violations, "downloadSessionIdleTimeoutMinutes",
                config.downloadSessionIdleTimeoutMinutes());

        requireRange(violations, "maxActiveUploadsPerAdmin", config.maxActiveUploadsPerAdmin(), 1, 100);
        requireRange(violations, "maxConcurrentInspectionsPerWorker",
                config.maxConcurrentInspectionsPerWorker(), 1, 16);
        requireRange(violations, "inspectionMaxExtractedEntries", config.inspectionMaxExtractedEntries(),
                1, 1_000_000);
        requireRange(violations, "maxAutoInspectionAttempts", config.maxAutoInspectionAttempts(), 1, 10);

        // Package size vs quota.
        if (config.maxPackageBytes() > 0 && config.totalQuotaBytes() > 0
                && config.maxPackageBytes() > config.totalQuotaBytes()) {
            violations.add(SoftwarePolicyConfig.Violation.of(
                    "maxPackageBytes", "error.software.settings.packageExceedsQuota"));
        }

        // Chunk size vs S3 multipart constraints, package size, and proxy body limit.
        if (config.chunkSizeBytes() > 0) {
            if (config.chunkSizeBytes() < S3_MIN_PART_SIZE) {
                violations.add(SoftwarePolicyConfig.Violation.of(
                        "chunkSizeBytes", "error.software.settings.chunkTooSmall", S3_MIN_PART_SIZE));
            }
            if (config.maxPackageBytes() > 0 && config.chunkSizeBytes() > config.maxPackageBytes()) {
                violations.add(SoftwarePolicyConfig.Violation.of(
                        "chunkSizeBytes", "error.software.settings.chunkExceedsPackage"));
            }
            if (config.maxPackageBytes() > 0 && config.chunkSizeBytes() >= S3_MIN_PART_SIZE
                    && partCount(config.maxPackageBytes(), config.chunkSizeBytes()) > S3_MAX_PART_COUNT) {
                violations.add(SoftwarePolicyConfig.Violation.of(
                        "chunkSizeBytes", "error.software.settings.chunkCountExceeded", S3_MAX_PART_COUNT));
            }
            if (limits != null && limits.proxyMaxBodyBytes() > 0
                    && config.chunkSizeBytes() > limits.proxyMaxBodyBytes()) {
                violations.add(SoftwarePolicyConfig.Violation.of(
                        "chunkSizeBytes", "error.software.settings.chunkExceedsProxyBody",
                        limits.proxyMaxBodyBytes()));
            }
        }

        // Inspection subprocess vs total deadline.
        if (config.inspectionSubprocessTimeoutSeconds() > 0 && config.inspectionTotalTimeoutSeconds() > 0
                && config.inspectionSubprocessTimeoutSeconds() > config.inspectionTotalTimeoutSeconds()) {
            violations.add(SoftwarePolicyConfig.Violation.of(
                    "inspectionSubprocessTimeoutSeconds",
                    "error.software.settings.subprocessExceedsTotal"));
        }

        // Soft memory limit vs deployment-declared worker hard limit.
        if (config.inspectionSoftMemoryLimitBytes() > 0 && limits != null
                && limits.workerAvailableMemoryBytes() > 0
                && config.inspectionSoftMemoryLimitBytes() > limits.workerAvailableMemoryBytes()) {
            violations.add(SoftwarePolicyConfig.Violation.of(
                    "inspectionSoftMemoryLimitBytes", "error.software.settings.softMemoryExceedsWorker",
                    limits.workerAvailableMemoryBytes()));
        }

        return violations;
    }

    private static long partCount(long packageBytes, long chunkBytes) {
        return (packageBytes + chunkBytes - 1) / chunkBytes;
    }

    private static void requirePositive(List<SoftwarePolicyConfig.Violation> violations,
                                        String field, long value) {
        if (value <= 0) {
            violations.add(SoftwarePolicyConfig.Violation.of(field, "error.software.settings.mustBePositive"));
        }
    }

    private static void requireRange(List<SoftwarePolicyConfig.Violation> violations,
                                     String field, int value, int min, int max) {
        if (value < min || value > max) {
            violations.add(SoftwarePolicyConfig.Violation.of(
                    field, "error.software.settings.outOfRange", min, max));
        }
    }
}
