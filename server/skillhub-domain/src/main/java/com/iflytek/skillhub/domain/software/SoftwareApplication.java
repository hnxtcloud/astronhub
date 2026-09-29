package com.iflytek.skillhub.domain.software;

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
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;

/**
 * A software application in the software center. Applications are a
 * platform-level collection and carry no namespace ownership; the slug is
 * unique inside the software center only.
 */
@Entity
@Table(name = "software_application")
public class SoftwareApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon_object_key", length = 512)
    private String iconObjectKey;

    @Column(name = "category_code", length = 64)
    private String categoryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SoftwareApplicationStatus status;

    @Column(name = "download_count", nullable = false)
    private Long downloadCount = 0L;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long entityVersion;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SoftwareApplication() {
    }

    public SoftwareApplication(String slug, String name, String description,
                               String iconObjectKey, String categoryCode, String createdBy) {
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.iconObjectKey = iconObjectKey;
        this.categoryCode = categoryCode;
        this.createdBy = createdBy;
        this.status = SoftwareApplicationStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now(Clock.systemUTC());
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public void updateProfile(String name, String description, String iconObjectKey, String categoryCode) {
        this.name = name;
        this.description = description;
        this.iconObjectKey = iconObjectKey;
        this.categoryCode = categoryCode;
    }

    public void publish() {
        this.status = SoftwareApplicationStatus.PUBLISHED;
    }

    public void takeDown() {
        this.status = SoftwareApplicationStatus.TAKEN_DOWN;
    }

    /**
     * Restores a taken-down application to published state. Independently
     * taken-down packages are not affected by this transition.
     */
    public void restore() {
        this.status = SoftwareApplicationStatus.PUBLISHED;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIconObjectKey() {
        return iconObjectKey;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public SoftwareApplicationStatus getStatus() {
        return status;
    }

    public Long getDownloadCount() {
        return downloadCount;
    }

    public long getEntityVersion() {
        return entityVersion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
