package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "plugin_project")
public class PluginProject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "owner_id", nullable = false, length = 128)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PluginVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PluginProjectStatus status;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "latest_release_id")
    private Long latestReleaseId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "download_count", nullable = false)
    private long downloadCount;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PluginProject() {
    }

    public PluginProject(Long namespaceId, String slug, String displayName, String ownerId,
                         PluginVisibility visibility) {
        this.namespaceId = namespaceId;
        this.slug = slug;
        this.displayName = displayName;
        this.ownerId = ownerId;
        this.visibility = visibility;
        this.status = PluginProjectStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Long getNamespaceId() { return namespaceId; }
    public String getSlug() { return slug; }
    public String getDisplayName() { return displayName; }
    public String getSummary() { return summary; }
    public String getOwnerId() { return ownerId; }
    public PluginVisibility getVisibility() { return visibility; }
    public PluginProjectStatus getStatus() { return status; }
    public boolean isHidden() { return hidden; }
    public Long getLatestReleaseId() { return latestReleaseId; }
    public long getViewCount() { return viewCount; }
    public long getDownloadCount() { return downloadCount; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSummary(String summary) { this.summary = summary; }
    public void pointToPublishedRelease(PluginRelease release) {
        if (release == null || release.getId() == null || release.getStatus() != PluginReleaseStatus.PUBLISHED) {
            throw new IllegalArgumentException("Latest plugin release must be persisted and published");
        }
        if (id == null || !id.equals(release.getProjectId())) {
            throw new IllegalArgumentException("Plugin release must belong to this project");
        }
        this.latestReleaseId = release.getId();
    }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public void archive() { this.status = PluginProjectStatus.ARCHIVED; }
    public void restore() { this.status = PluginProjectStatus.ACTIVE; }
    public void hide() { this.hidden = true; }
    public void show() { this.hidden = false; }
}
