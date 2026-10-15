package com.iflytek.skillhub.domain.plugin;

import java.util.Optional;

public interface PluginImportBatchRepository {
    Optional<PluginImportBatch> findById(String id);

    java.util.List<PluginImportBatch> findTop100ByExpiresAtBefore(java.time.Instant expiry);

    void deleteById(String id);

    PluginImportBatch save(PluginImportBatch value);
}
