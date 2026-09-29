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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;

/**
 * An immutable installable package revision of a release. Content changes go
 * through a new revision; only supplemental descriptions are auditable edits.
 * The original full package version (e.g. RPM {@code epoch:version-release}
 * or the DEB complete version) is preserved verbatim.
 */
@Entity
@Table(name = "software_package_revision", uniqueConstraints = {
        @UniqueConstraint(name = "uk_software_package_app_sha256",
                columnNames = {"application_id", "sha256"}),
        @UniqueConstraint(name = "uk_software_package_revision_no",
                columnNames = {"release_id", "target_os", "target_arch", "target_distro",
                        "original_full_version", "revision_no"})
})
public class SoftwarePackageRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "release_id", nullable = false)
    private Long releaseId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_os", nullable = false, length = 20)
    private SoftwareTargetOs targetOs;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_arch", nullable = false, length = 20)
    private SoftwareTargetArch targetArch;

    @Column(name = "target_distro", length = 64)
    private String targetDistro;

    @Column(name = "applicability_note", length = 512)
    private String applicabilityNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SoftwarePackageFormat format;

    @Column(name = "original_full_version", nullable = false, length = 255)
    private String originalFullVersion;

    /**
     * Structured raw metadata fields exactly as extracted from the package
     * (JSON object). Never modified by later human confirmations.
     */
    @Column(name = "raw_version_fields")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String rawVersionFields;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    /** Immutable storage object key; generated server-side, never public. */
    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SoftwarePackageStatus status;

    @Column(name = "confirmed_by", length = 128)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long entityVersion;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SoftwarePackageRevision() {
    }

    public SoftwarePackageRevision(Long releaseId,
                                   Long applicationId,
                                   int revisionNo,
                                   SoftwareTargetOs targetOs,
                                   SoftwareTargetArch targetArch,
                                   String targetDistro,
                                   String applicabilityNote,
                                   SoftwarePackageFormat format,
                                   String originalFullVersion,
                                   String rawVersionFields,
                                   String fileName,
                                   String sha256,
                                   long byteSize,
                                   String objectKey,
                                   String createdBy) {
        this.releaseId = releaseId;
        this.applicationId = applicationId;
        this.revisionNo = revisionNo;
        this.targetOs = targetOs;
        this.targetArch = targetArch;
        this.targetDistro = targetDistro;
        this.applicabilityNote = applicabilityNote;
        this.format = format;
        this.originalFullVersion = originalFullVersion;
        this.rawVersionFields = rawVersionFields;
        this.fileName = fileName;
        this.sha256 = sha256;
        this.byteSize = byteSize;
        this.objectKey = objectKey;
        this.createdBy = createdBy;
        this.status = SoftwarePackageStatus.DRAFT;
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

    /** Records administrator confirmation of the package metadata. */
    public void confirm(String confirmedBy) {
        this.confirmedBy = confirmedBy;
        this.confirmedAt = Instant.now(Clock.systemUTC());
    }

    /** Supplemental description edit; never touches target/version/file facts. */
    public void updateApplicabilityNote(String applicabilityNote) {
        this.applicabilityNote = applicabilityNote;
    }

    public void publish() {
        this.status = SoftwarePackageStatus.PUBLISHED;
        this.publishedAt = Instant.now(Clock.systemUTC());
    }

    public void takeDown() {
        this.status = SoftwarePackageStatus.TAKEN_DOWN;
    }

    public void restore() {
        this.status = SoftwarePackageStatus.PUBLISHED;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public int getRevisionNo() {
        return revisionNo;
    }

    public SoftwareTargetOs getTargetOs() {
        return targetOs;
    }

    public SoftwareTargetArch getTargetArch() {
        return targetArch;
    }

    public String getTargetDistro() {
        return targetDistro;
    }

    public String getApplicabilityNote() {
        return applicabilityNote;
    }

    public SoftwarePackageFormat getFormat() {
        return format;
    }

    public String getOriginalFullVersion() {
        return originalFullVersion;
    }

    public String getRawVersionFields() {
        return rawVersionFields;
    }

    public String getFileName() {
        return fileName;
    }

    public String getSha256() {
        return sha256;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public SoftwarePackageStatus getStatus() {
        return status;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
