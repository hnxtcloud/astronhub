package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mutable selection of append-only configuration revisions; snapshots never read this after
 * approval.
 */
@Entity
@Table(name = "plugin_git_namespace_target")
public class PluginGitNamespaceTarget {
    @Id
    @Column(name = "namespace_id")
    private Long namespaceId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    protected PluginGitNamespaceTarget() {}

    public PluginGitNamespaceTarget(Long namespaceId, Long targetId) {
        this.namespaceId = namespaceId;
        this.targetId = targetId;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public Long getTargetId() {
        return targetId;
    }
}
