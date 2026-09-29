package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SoftwarePolicyValidatorTest {

    private final SoftwarePolicyDeploymentLimits limits = new SoftwarePolicyDeploymentLimits(
            100L * 1024 * 1024, 1024L * 1024 * 1024, 512L * 1024 * 1024);

    @Test
    void defaults_passValidation() {
        List<SoftwarePolicyConfig.Violation> violations =
                SoftwarePolicyValidator.validate(SoftwarePolicyConfig.defaults(), limits);
        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsNonPositiveValues() {
        SoftwarePolicyConfig config = new SoftwarePolicyConfig(0, 500L * 1024 * 1024 * 1024, 2,
                8L * 1024 * 1024, 1440, 168, 5, 1, 900, 60, 512L * 1024 * 1024,
                20L * 1024 * 1024 * 1024, 10_000, 3, 1440);
        List<SoftwarePolicyConfig.Violation> violations = SoftwarePolicyValidator.validate(config, limits);
        assertThat(violations).extracting(SoftwarePolicyConfig.Violation::field)
                .containsExactly("maxPackageBytes");
        assertThat(violations.get(0).messageCode()).isEqualTo("error.software.settings.mustBePositive");
    }

    @Test
    void rejectsPackageLimitAboveQuota() {
        SoftwarePolicyConfig config = new SoftwarePolicyConfig(600L * 1024 * 1024 * 1024,
                500L * 1024 * 1024 * 1024, 2, 8L * 1024 * 1024, 1440, 168, 5, 1, 900, 60,
                512L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);
        List<SoftwarePolicyConfig.Violation> violations = SoftwarePolicyValidator.validate(config, limits);
        assertThat(violations).extracting(SoftwarePolicyConfig.Violation::messageCode)
                .contains("error.software.settings.packageExceedsQuota");
    }

    @Test
    void rejectsChunkBelowS3MultipartMinimum() {
        SoftwarePolicyConfig config = new SoftwarePolicyConfig(5L * 1024 * 1024 * 1024,
                500L * 1024 * 1024 * 1024, 2, 4L * 1024 * 1024, 1440, 168, 5, 1, 900, 60,
                512L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);
        List<SoftwarePolicyConfig.Violation> violations = SoftwarePolicyValidator.validate(config, limits);
        assertThat(violations).extracting(SoftwarePolicyConfig.Violation::field)
                .contains("chunkSizeBytes");
        assertThat(violations).extracting(SoftwarePolicyConfig.Violation::messageCode)
                .contains("error.software.settings.chunkTooSmall");
    }

    @Test
    void rejectsChunkCountAboveS3Limit() {
        // 5 GiB package with 8 MiB chunks = 640 parts (OK); with the max package
        // bumped above 80 GiB the count would exceed 10000 — emulate via a small
        // chunk against a large package while staying above the 5 MiB floor.
        SoftwarePolicyConfig config = new SoftwarePolicyConfig(
                200L * 1024 * 1024 * 1024, 500L * 1024 * 1024 * 1024, 2,
                6L * 1024 * 1024, 1440, 168, 5, 1, 900, 60,
                512L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);
        List<SoftwarePolicyConfig.Violation> violations = SoftwarePolicyValidator.validate(config, limits);
        assertThat(violations).extracting(SoftwarePolicyConfig.Violation::messageCode)
                .contains("error.software.settings.chunkCountExceeded");
    }

    @Test
    void rejectsChunkAboveProxyBodyLimit() {
        SoftwarePolicyConfig config = new SoftwarePolicyConfig(5L * 1024 * 1024 * 1024,
                500L * 1024 * 1024 * 1024, 2, 128L * 1024 * 1024, 1440, 168, 5, 1, 900, 60,
                512L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);
        List<SoftwarePolicyConfig.Violation> violations = SoftwarePolicyValidator.validate(config, limits);
        assertThat(violations).extracting(SoftwarePolicyConfig.Violation::messageCode)
                .contains("error.software.settings.chunkExceedsProxyBody");
    }

    @Test
    void rejectsSubprocessTimeoutAboveTotalDeadline() {
        SoftwarePolicyConfig config = new SoftwarePolicyConfig(5L * 1024 * 1024 * 1024,
                500L * 1024 * 1024 * 1024, 2, 8L * 1024 * 1024, 1440, 168, 5, 1, 60, 900,
                512L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);
        List<SoftwarePolicyConfig.Violation> violations = SoftwarePolicyValidator.validate(config, limits);
        assertThat(violations).extracting(SoftwarePolicyConfig.Violation::messageCode)
                .contains("error.software.settings.subprocessExceedsTotal");
    }

    @Test
    void rejectsSoftMemoryAboveWorkerHardLimit() {
        SoftwarePolicyConfig config = new SoftwarePolicyConfig(5L * 1024 * 1024 * 1024,
                500L * 1024 * 1024 * 1024, 2, 8L * 1024 * 1024, 1440, 168, 5, 1, 900, 60,
                900L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);
        List<SoftwarePolicyConfig.Violation> violations = SoftwarePolicyValidator.validate(config, limits);
        assertThat(violations).extracting(SoftwarePolicyConfig.Violation::messageCode)
                .contains("error.software.settings.softMemoryExceedsWorker");
    }

    @Test
    void rejectsOutOfRangeIntegerFields() {
        SoftwarePolicyConfig config = new SoftwarePolicyConfig(5L * 1024 * 1024 * 1024,
                500L * 1024 * 1024 * 1024, 0, 8L * 1024 * 1024, 1440, 168, 5, 99, 900, 60,
                512L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);
        List<SoftwarePolicyConfig.Violation> violations = SoftwarePolicyValidator.validate(config, limits);
        assertThat(violations).extracting(SoftwarePolicyConfig.Violation::field)
                .contains("maxActiveUploadsPerAdmin", "maxConcurrentInspectionsPerWorker");
        assertThat(violations).extracting(SoftwarePolicyConfig.Violation::messageCode)
                .containsOnly("error.software.settings.outOfRange");
    }
}
