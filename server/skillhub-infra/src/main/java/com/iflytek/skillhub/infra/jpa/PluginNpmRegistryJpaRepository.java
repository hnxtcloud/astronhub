package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginNpmRegistry;
import com.iflytek.skillhub.domain.plugin.PluginNpmRegistryRepository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginNpmRegistryJpaRepository
        extends JpaRepository<PluginNpmRegistry, Long>, PluginNpmRegistryRepository {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update PluginNpmRegistry r set r.defaultRegistry=false where r.defaultRegistry=true")
    void clearDefaultRegistries();
}
