package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Independent dependency inventory: never a plugin project, version, or review. */
@Entity
@Table(name = "plugin_npm_dependency_upload")
public class PluginNpmDependencyUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploaded_by", nullable = false, length = 128)
    private String uploadedBy;

    @Column(nullable = false, length = 2000)
    private String source;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 64)
    private String version;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "object_key", nullable = false, length = 1000)
    private String objectKey;

    @Column(name = "manifest_json", nullable = false, columnDefinition = "TEXT")
    private String manifestJson;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshot;

    @Column(nullable = false, length = 32)
    private String state = "AWAITING_CONFIRMATION";

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 80)
    private String lastError;

    @Column(columnDefinition = "TEXT")
    private String receipt;

    @Column(name = "attempt_token", length = 36)
    private String attemptToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected PluginNpmDependencyUpload() {}

    public PluginNpmDependencyUpload(
            String actor,
            String source,
            String name,
            String version,
            String sha256,
            String key,
            String manifest,
            String snapshot) {
        uploadedBy = actor;
        this.source = source;
        this.name = name;
        this.version = version;
        this.sha256 = sha256;
        objectKey = key;
        manifestJson = manifest;
        this.snapshot = snapshot;
    }

    public String confirm() {
        if (state.equals("SUCCEEDED")
                || (state.equals("RUNNING") && leaseUntil.isAfter(Instant.now()))) return null;
        attemptCount++;
        attemptToken = java.util.UUID.randomUUID().toString();
        leaseUntil = Instant.now().plusSeconds(300);
        state = "RUNNING";
        return attemptToken;
    }

    public void finish(String token, String result, String error) {
        if (!state.equals("RUNNING") || !java.util.Objects.equals(token, attemptToken)) return;
        state = error == null ? "SUCCEEDED" : "FAILED";
        receipt = result;
        lastError = error;
        leaseUntil = null;
    }

    public Long getId() {
        return id;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public String getSource() {
        return source;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getSha256() {
        return sha256;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getManifestJson() {
        return manifestJson;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public String getState() {
        return state;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public String getReceipt() {
        return receipt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
