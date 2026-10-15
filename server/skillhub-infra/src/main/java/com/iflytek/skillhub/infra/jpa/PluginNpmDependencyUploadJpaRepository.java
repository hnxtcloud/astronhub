package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginNpmDependencyUpload;
import com.iflytek.skillhub.domain.plugin.PluginNpmDependencyUploadRepository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginNpmDependencyUploadJpaRepository
        extends JpaRepository<PluginNpmDependencyUpload, Long>,
                PluginNpmDependencyUploadRepository {
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT * FROM plugin_npm_dependency_upload WHERE id=:id FOR UPDATE",
            nativeQuery = true)
    java.util.Optional<PluginNpmDependencyUpload> findByIdForUpdate(Long id);
}
