package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only Git target configuration revision. Credential reference is never secret material. */
@Entity
@Table(name = "plugin_git_target")
public class PluginGitTarget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(nullable = false, length = 2000)
    private String remote;

    @Column(name = "fetch_url", nullable = false, length = 2000)
    private String fetchUrl;

    @Column(nullable = false, length = 200)
    private String branch;

    @Column(name = "credential_ref", nullable = false, length = 120)
    private String credentialRef;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PluginGitTarget() {}

    public PluginGitTarget(
            Long namespaceId,
            String remote,
            String fetchUrl,
            String branch,
            String ref,
            String actor) {
        this.namespaceId = namespaceId;
        this.remote = remote;
        this.fetchUrl = fetchUrl;
        this.branch = branch;
        credentialRef = ref;
        createdBy = actor;
    }

    public Long getId() {
        return id;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public String getRemote() {
        return remote;
    }

    public String getFetchUrl() {
        return fetchUrl;
    }

    public String getBranch() {
        return branch;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
