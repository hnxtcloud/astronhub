package com.iflytek.skillhub.domain.plugin;

import java.util.Optional;

public interface PluginImportSettingsRepository {
    Optional<PluginImportSettings> findById(Long id);

    PluginImportSettings save(PluginImportSettings value);
}
