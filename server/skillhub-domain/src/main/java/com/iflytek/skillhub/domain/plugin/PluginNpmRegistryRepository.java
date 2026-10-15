package com.iflytek.skillhub.domain.plugin;

import java.util.List;
import java.util.Optional;

public interface PluginNpmRegistryRepository {
    void clearDefaultRegistries();

    PluginNpmRegistry save(PluginNpmRegistry registry);

    Optional<PluginNpmRegistry> findById(Long id);

    List<PluginNpmRegistry> findAllByOrderByIdDesc();
}
