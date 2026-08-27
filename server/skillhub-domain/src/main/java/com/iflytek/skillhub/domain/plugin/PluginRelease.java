package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "plugin_release")
public class PluginRelease {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(nullable = false, length = 64)
    private String version;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private PluginReleaseStatus status;
    @Column(columnDefinition = "TEXT")
    private String changelog;
    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected PluginRelease() {}

    public PluginRelease(Long projectId, String version, String createdBy) {
        this.projectId = projectId;
        this.version = version;
        this.createdBy = createdBy;
        this.status = PluginReleaseStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() { createdAt = Instant.now(Clock.systemUTC()); }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getVersion() { return version; }
    public PluginReleaseStatus getStatus() { return status; }
    public String getChangelog() { return changelog; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setChangelog(String changelog) { this.changelog = changelog; }
}
