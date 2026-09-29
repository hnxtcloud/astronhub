package com.iflytek.skillhub.domain.software;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Clock;
import java.time.Instant;

/**
 * Administrator-set download recommendation binding a specific package
 * revision to an adaptation scope (OS/architecture/distribution/format).
 * Conflicting recommendations for the same scope are rejected; recommendations
 * are cleared when the recommended package (or its application) is taken down.
 */
@Entity
@Table(name = "software_recommendation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_software_recommendation_scope",
                columnNames = {"release_id", "target_os", "target_arch", "target_distro", "format"})
})
public class SoftwareRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "release_id", nullable = false)
    private Long releaseId;

    @Column(name = "package_revision_id", nullable = false)
    private Long packageRevisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_os", nullable = false, length = 20)
    private SoftwareTargetOs targetOs;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_arch", nullable = false, length = 20)
    private SoftwareTargetArch targetArch;

    @Column(name = "target_distro", length = 64)
    private String targetDistro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SoftwarePackageFormat format;

    @Column(name = "note", length = 512)
    private String note;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SoftwareRecommendation() {
    }

    public SoftwareRecommendation(Long releaseId,
                                  Long packageRevisionId,
                                  SoftwareTargetOs targetOs,
                                  SoftwareTargetArch targetArch,
                                  String targetDistro,
                                  SoftwarePackageFormat format,
                                  String note,
                                  String createdBy) {
        this.releaseId = releaseId;
        this.packageRevisionId = packageRevisionId;
        this.targetOs = targetOs;
        this.targetArch = targetArch;
        this.targetDistro = targetDistro;
        this.format = format;
        this.note = note;
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() {
        return id;
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public Long getPackageRevisionId() {
        return packageRevisionId;
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

    public SoftwarePackageFormat getFormat() {
        return format;
    }

    public String getNote() {
        return note;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
