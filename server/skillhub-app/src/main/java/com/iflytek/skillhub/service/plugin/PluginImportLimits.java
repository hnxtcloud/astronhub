package com.iflytek.skillhub.service.plugin;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import io.swagger.v3.oas.annotations.media.Schema;

/** Persisted administrator-controlled budgets, snapshotted for each scan. */
@Schema(requiredProperties={"packageBytes","repositoryBytes","expandedBytes","files","candidates"})
public record PluginImportLimits(
        long packageBytes, long repositoryBytes, long expandedBytes, int files, int candidates) {
    public static PluginImportLimits defaults() {
        return new PluginImportLimits(104857600L, 524288000L, 2147483648L, 20000, 100);
    }

    public void validate() {
        if (packageBytes < 1
                || repositoryBytes < packageBytes
                || repositoryBytes > 2147483648L
                || expandedBytes < 1
                || expandedBytes > 21474836480L
                || files < 1
                || files > 200000
                || candidates < 1
                || candidates > 1000)
            throw new DomainBadRequestException("error.plugin.importLimits");
    }
}
