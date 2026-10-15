package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "plugin_import_batch")
public class PluginImportBatch {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(name = "owner_id", nullable = false, length = 128)
    private String ownerId;

    @Column(nullable = false)
    private boolean repository;

    @Column(name = "preview_json", nullable = false, columnDefinition = "TEXT")
    private String previewJson;

    @Column(name = "limits_json", nullable = false, columnDefinition = "TEXT")
    private String limitsJson;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PluginImportBatch() {}

    public PluginImportBatch(
            String id,
            Long namespaceId,
            String ownerId,
            boolean repository,
            String previewJson,
            String limitsJson,
            Instant expiresAt) {
        this.id = id;
        this.namespaceId = namespaceId;
        this.ownerId = ownerId;
        this.repository = repository;
        this.previewJson = previewJson;
        this.limitsJson = limitsJson;
        this.expiresAt = expiresAt;
    }

    public String getId() {
        return id;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getLimitsJson() {
        return limitsJson;
    }

    public boolean isRepository() {
        return repository;
    }

    public String getPreviewJson() {
        return previewJson;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
