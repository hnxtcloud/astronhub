package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A repository branch retains its namespace identity across configuration revisions. */
@Entity
@Table(name = "plugin_git_destination_claim")
public class PluginGitDestinationClaim {
    @Id
    @Column(name = "target_key", length = 64)
    private String targetKey;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    protected PluginGitDestinationClaim() {}

    public PluginGitDestinationClaim(String key, Long namespaceId) {
        targetKey = key;
        this.namespaceId = namespaceId;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }
}
