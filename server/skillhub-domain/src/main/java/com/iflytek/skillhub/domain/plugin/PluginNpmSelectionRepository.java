package com.iflytek.skillhub.domain.plugin;

import java.util.Optional;

public interface PluginNpmSelectionRepository {
    PluginNpmSelection save(PluginNpmSelection value);

    Optional<PluginNpmSelection> findById(Long releaseId);
}
