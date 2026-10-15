package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "plugin_npm_selection")
public class PluginNpmSelection {
    @Id
    @Column(name = "release_id")
    private Long releaseId;

    @Column(name = "registry_id", nullable = false)
    private Long registryId;

    protected PluginNpmSelection() {}

    public PluginNpmSelection(Long releaseId, Long registryId) {
        this.releaseId = releaseId;
        this.registryId = registryId;
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public Long getRegistryId() {
        return registryId;
    }
}
