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
 * A chunked upload session for one package revision. The application scope and
 * owner are bound at creation and never derived from chunk requests. Policy
 * values that govern the session (chunk size, idle timeout) are snapshotted so
 * later configuration changes never move an in-flight session's boundaries.
 */
@Entity
@Table(name = "software_upload_session")
public class SoftwareUploadSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "release_id", nullable = false)
    private Long releaseId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "owner_user_id", nullable = false, length = 128)
    private String ownerUserId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SoftwarePackageFormat format;

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

    @Column(name = "original_full_version", nullable = false, length = 255)
    private String originalFullVersion;

    @Column(name = "expected_byte_size", nullable = false)
    private long expectedByteSize;

    /** Chunk boundary snapshot from the policy revision active at creation. */
    @Column(name = "chunk_size_bytes", nullable = false)
    private long chunkSizeBytes;

    /** Policy revision number this session was admitted under. */
    @Column(name = "policy_revision", nullable = false)
    private long policyRevision;

    @Column(name = "upload_idle_timeout_minutes", nullable = false)
    private int uploadIdleTimeoutMinutes;

    @Column(name = "reserved_bytes", nullable = false)
    private long reservedBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SoftwareUploadSessionStatus status;

    /** Storage-provider session reference (local temp dir id / S3 multipart upload id). */
    @Column(name = "storage_session_ref", length = 256)
    private String storageSessionRef;

    /** Immutable final object key, generated server-side at creation. */
    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "completed_revision_id")
    private Long completedRevisionId;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long entityVersion;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SoftwareUploadSession() {
    }

    public SoftwareUploadSession(Long releaseId,
                                 Long applicationId,
                                 String ownerUserId,
                                 String fileName,
                                 SoftwarePackageFormat format,
                                 SoftwareTargetOs targetOs,
                                 SoftwareTargetArch targetArch,
                                 String targetDistro,
                                 String applicabilityNote,
                                 String originalFullVersion,
                                 long expectedByteSize,
                                 long chunkSizeBytes,
                                 long policyRevision,
                                 int uploadIdleTimeoutMinutes,
                                 long reservedBytes,
                                 String objectKey,
                                 Instant now) {
        this.releaseId = releaseId;
        this.applicationId = applicationId;
        this.ownerUserId = ownerUserId;
        this.fileName = fileName;
        this.format = format;
        this.targetOs = targetOs;
        this.targetArch = targetArch;
        this.targetDistro = targetDistro;
        this.applicabilityNote = applicabilityNote;
        this.originalFullVersion = originalFullVersion;
        this.expectedByteSize = expectedByteSize;
        this.chunkSizeBytes = chunkSizeBytes;
        this.policyRevision = policyRevision;
        this.uploadIdleTimeoutMinutes = uploadIdleTimeoutMinutes;
        this.reservedBytes = reservedBytes;
        this.status = SoftwareUploadSessionStatus.ACTIVE;
        this.objectKey = objectKey;
        this.lastActivityAt = now;
        this.expiresAt = now.plusSeconds(uploadIdleTimeoutMinutes * 60L);
        this.createdBy = ownerUserId;
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

    public Long getId() {
        return id;
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public String getFileName() {
        return fileName;
    }

    public SoftwarePackageFormat getFormat() {
        return format;
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

    public String getOriginalFullVersion() {
        return originalFullVersion;
    }

    public long getExpectedByteSize() {
        return expectedByteSize;
    }

    public long getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public long getPolicyRevision() {
        return policyRevision;
    }

    public int getUploadIdleTimeoutMinutes() {
        return uploadIdleTimeoutMinutes;
    }

    public long getReservedBytes() {
        return reservedBytes;
    }

    public SoftwareUploadSessionStatus getStatus() {
        return status;
    }

    public String getStorageSessionRef() {
        return storageSessionRef;
    }

    public void assignStorageSessionRef(String storageSessionRef) {
        this.storageSessionRef = storageSessionRef;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public Long getCompletedRevisionId() {
        return completedRevisionId;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
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

    public int totalChunks() {
        return (int) ((expectedByteSize + chunkSizeBytes - 1) / chunkSizeBytes);
    }

    /** Expected length of the given 1-based chunk; last chunk may be short. */
    public long expectedChunkLength(int chunkNo) {
        if (chunkNo < 1 || chunkNo > totalChunks()) {
            throw new IllegalArgumentException("chunkNo out of range: " + chunkNo);
        }
        if (chunkNo < totalChunks()) {
            return chunkSizeBytes;
        }
        return expectedByteSize - (long) (chunkNo - 1) * chunkSizeBytes;
    }

    public void requireOwner(String userId) {
        if (!ownerUserId.equals(userId)) {
            throw new com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException(
                    "error.software.upload.sessionNotFound", id);
        }
    }

    public void requireActive() {
        if (status != SoftwareUploadSessionStatus.ACTIVE) {
            throw new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                    "error.software.upload.sessionNotActive", status);
        }
    }

    /** Records an accepted chunk and extends the idle window from the activity time. */
    public void recordChunkActivity(Instant activityAt) {
        this.lastActivityAt = activityAt;
        this.expiresAt = activityAt.plusSeconds(uploadIdleTimeoutMinutes * 60L);
    }

    public void markCompleting() {
        if (status != SoftwareUploadSessionStatus.ACTIVE
                && status != SoftwareUploadSessionStatus.COMPLETING) {
            throw new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                    "error.software.upload.sessionNotActive", status);
        }
        this.status = SoftwareUploadSessionStatus.COMPLETING;
    }

    public void markCompleted(Long packageRevisionId) {
        this.status = SoftwareUploadSessionStatus.COMPLETED;
        this.completedRevisionId = packageRevisionId;
        this.expiresAt = Instant.now(Clock.systemUTC());
    }

    public void markCancelled() {
        this.status = SoftwareUploadSessionStatus.CANCELLED;
        this.expiresAt = Instant.now(Clock.systemUTC());
    }

    /** A crashed COMPLETING session ends as CANCELLED via the cleanup task. */
    public void markCancelledFromCompleting() {
        if (status != SoftwareUploadSessionStatus.COMPLETING) {
            throw new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                    "error.software.upload.sessionNotActive", status);
        }
        markCancelled();
    }

    public void markExpired() {
        this.status = SoftwareUploadSessionStatus.EXPIRED;
        this.expiresAt = Instant.now(Clock.systemUTC());
    }

    /** True when the session idled past its snapshot timeout. */
    public boolean isIdleExpired(Instant now) {
        return status == SoftwareUploadSessionStatus.ACTIVE && now.isAfter(expiresAt);
    }
}
