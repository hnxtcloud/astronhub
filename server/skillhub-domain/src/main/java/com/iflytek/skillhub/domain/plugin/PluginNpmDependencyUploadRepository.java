package com.iflytek.skillhub.domain.plugin;

import java.util.List;
import java.util.Optional;

public interface PluginNpmDependencyUploadRepository {
    PluginNpmDependencyUpload save(PluginNpmDependencyUpload value);

    Optional<PluginNpmDependencyUpload> findByIdForUpdate(Long id);

    List<PluginNpmDependencyUpload> findTop100ByOrderByIdDesc();
}
