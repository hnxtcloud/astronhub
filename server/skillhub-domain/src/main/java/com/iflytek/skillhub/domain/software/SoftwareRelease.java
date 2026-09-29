package com.iflytek.skillhub.domain.software;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

/**
 * An administrator-confirmed display version of an application. Releases
 * group package revisions across platforms; the display version text is kept
 * verbatim (only outer whitespace trimmed) and never interpreted as SemVer.
 */
@Entity
@Table(name = "software_release")
public class SoftwareRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "display_version", nullable = false, length = 255)
    private String displayVersion;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SoftwareRelease() {
    }

    public SoftwareRelease(Long applicationId, String displayVersion, String releaseNotes, String createdBy) {
        this.applicationId = applicationId;
        this.displayVersion = displayVersion;
        this.releaseNotes = releaseNotes;
        this.createdBy = createdBy;
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

    public void updateReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public String getDisplayVersion() {
        return displayVersion;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }
}
