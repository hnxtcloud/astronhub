package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registry revisions are append-only; old publication snapshots remain bound to the old revision.
 */
@Entity
@Table(name = "plugin_npm_registry")
public class PluginNpmRegistry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2000)
    private String registry;

    @Column(name = "credential_ref", nullable = false, length = 120)
    private String credentialRef;

    @Column(name = "default_registry", nullable = false)
    private boolean defaultRegistry;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PluginNpmRegistry() {}

    public PluginNpmRegistry(
            String name, String registry, String ref, boolean isDefault, String actor) {
        this.name = name;
        this.registry = registry;
        credentialRef = ref;
        defaultRegistry = isDefault;
        createdBy = actor;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRegistry() {
        return registry;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public boolean isDefaultRegistry() {
        return defaultRegistry;
    }

    public void clearDefault() {
        defaultRegistry = false;
    }
}
