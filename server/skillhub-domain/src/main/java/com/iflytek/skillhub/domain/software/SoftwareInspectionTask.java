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
 * A persisted static-inspection task for one immutable package revision.
 * Created when an upload of an auto-inspected format completes; claimed by an
 * inspector worker through the restricted internal API with a short
 * transaction and an attempt token that makes stale results rejectable.
 */
@Entity
@Table(name = "software_inspection_task", uniqueConstraints = {
        @UniqueConstraint(name = "uk_software_inspection_revision", columnNames = "package_revision_id")})
public class SoftwareInspectionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_revision_id", nullable = false)
    private Long packageRevisionId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SoftwareInspectionTaskStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "attempt_token", length = 64)
    private String attemptToken;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "next_attempt_not_before")
    private Instant nextAttemptNotBefore;

    @Column(name = "manual_retry_count", nullable = false)
    private int manualRetryCount;

    @Column(name = "policy_revision", nullable = false)
    private long policyRevision;

    @Column(name = "total_timeout_seconds", nullable = false)
    private long totalTimeoutSeconds;

    @Column(name = "subprocess_timeout_seconds", nullable = false)
    private long subprocessTimeoutSeconds;

    @Column(name = "soft_memory_limit_bytes", nullable = false)
    private long softMemoryLimitBytes;

    @Column(name = "max_extracted_bytes", nullable = false)
    private long maxExtractedBytes;

    @Column(name = "max_extracted_entries", nullable = false)
    private int maxExtractedEntries;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SoftwarePackageFormat format;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long entityVersion;

    protected SoftwareInspectionTask() {
    }

    /**
     * Creates a PENDING task. The initial policy snapshot comes from the
     * active policy at creation time; claiming refreshes it.
     */
    public SoftwareInspectionTask(Long packageRevisionId,
                                  Long applicationId,
                                  int maxAttempts,
                                  SoftwarePolicySnapshot policy,
                                  String objectKey,
                                  SoftwarePackageFormat format) {
        this.packageRevisionId = packageRevisionId;
        this.applicationId = applicationId;
        this.status = SoftwareInspectionTaskStatus.PENDING;
        this.attemptCount = 0;
        this.maxAttempts = maxAttempts;
        this.manualRetryCount = 0;
        applyPolicySnapshot(policy);
        this.objectKey = objectKey;
        this.format = format;
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

    /**
     * Claims the task for a worker: allocates the next attempt number and a
     * fresh attempt token, and refreshes the policy snapshot so the attempt is
     * judged against the currently active dynamic policy.
     */
    public void claim(String workerId, String token, SoftwarePolicySnapshot policy, Instant now,
                      Instant leaseExpiresAt) {
        this.status = SoftwareInspectionTaskStatus.LEASED;
        this.leaseOwner = workerId;
        this.attemptToken = token;
        this.leaseExpiresAt = leaseExpiresAt;
        this.nextAttemptNotBefore = null;
        this.attemptCount++;
        applyPolicySnapshot(policy);
        this.updatedAt = now;
    }

    /** Extends the lease while the attempt is still running. */
    public boolean heartbeat(String workerId, String attemptToken, Instant leaseExpiresAt) {
        if (!holdsLease(workerId, attemptToken)) {
            return false;
        }
        this.leaseExpiresAt = leaseExpiresAt;
        this.updatedAt = Instant.now(Clock.systemUTC());
        return true;
    }

    /** True when the worker still holds the current attempt's lease. */
    public boolean holdsLease(String workerId, String attemptToken) {
        return status == SoftwareInspectionTaskStatus.LEASED
                && this.attemptToken != null && this.attemptToken.equals(attemptToken)
                && workerId != null && workerId.equals(this.leaseOwner);
    }

    /**
     * Records a terminal outcome (SUCCEEDED, BASIC_FAILED, METADATA_FAILED) or
     * schedules the next automatic retry for a recoverable infrastructure
     * failure. The caller has verified the lease before mutating anything.
     */
    public void applyOutcome(SoftwareInspectionOutcome outcome, Instant now, Instant nextAttemptNotBefore) {
        switch (outcome) {
            case SUCCEEDED -> this.status = SoftwareInspectionTaskStatus.SUCCEEDED;
            case BASIC_FAILED -> this.status = SoftwareInspectionTaskStatus.BASIC_FAILED;
            case METADATA_FAILED -> this.status = SoftwareInspectionTaskStatus.METADATA_FAILED;
            case INFRA_FAILED -> {
                if (attemptCount >= maxAttempts) {
                    this.status = SoftwareInspectionTaskStatus.EXHAUSTED;
                } else {
                    this.status = SoftwareInspectionTaskStatus.PENDING;
                    this.nextAttemptNotBefore = nextAttemptNotBefore;
                }
            }
        }
        clearLease();
        this.updatedAt = now;
    }

    /**
     * Marks the task exhausted when a lease expired without any result and the
     * auto-retry budget is spent. Returns false when the budget allows
     * reclaiming instead.
     */
    public boolean exhaustedByExpiredLease() {
        return attemptCount >= maxAttempts;
    }

    /** Closes a task whose lease expired without a result and without budget left. */
    public void exhaustFromExpiredLease(Instant now) {
        this.status = SoftwareInspectionTaskStatus.EXHAUSTED;
        clearLease();
        this.updatedAt = now;
    }

    /**
     * Administrator-triggered controlled retry: resets the task to PENDING
     * with a fresh auto-retry budget. Manual retries have their own budget.
     */
    public void manualRetry(Instant now) {
        this.status = SoftwareInspectionTaskStatus.PENDING;
        this.nextAttemptNotBefore = null;
        this.attemptCount = 0;
        this.manualRetryCount++;
        clearLease();
        this.updatedAt = now;
    }

    private void clearLease() {
        this.attemptToken = null;
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
    }

    private void applyPolicySnapshot(SoftwarePolicySnapshot policy) {
        SoftwarePolicyConfig config = policy.config();
        this.policyRevision = policy.revision();
        this.totalTimeoutSeconds = config.inspectionTotalTimeoutSeconds();
        this.subprocessTimeoutSeconds = config.inspectionSubprocessTimeoutSeconds();
        this.softMemoryLimitBytes = config.inspectionSoftMemoryLimitBytes();
        this.maxExtractedBytes = config.inspectionMaxExtractedBytes();
        this.maxExtractedEntries = config.inspectionMaxExtractedEntries();
    }

    public Long getId() {
        return id;
    }

    public Long getPackageRevisionId() {
        return packageRevisionId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public SoftwareInspectionTaskStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getAttemptToken() {
        return attemptToken;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public Instant getNextAttemptNotBefore() {
        return nextAttemptNotBefore;
    }

    public int getManualRetryCount() {
        return manualRetryCount;
    }

    public long getPolicyRevision() {
        return policyRevision;
    }

    public long getTotalTimeoutSeconds() {
        return totalTimeoutSeconds;
    }

    public long getSubprocessTimeoutSeconds() {
        return subprocessTimeoutSeconds;
    }

    public long getSoftMemoryLimitBytes() {
        return softMemoryLimitBytes;
    }

    public long getMaxExtractedBytes() {
        return maxExtractedBytes;
    }

    public int getMaxExtractedEntries() {
        return maxExtractedEntries;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public SoftwarePackageFormat getFormat() {
        return format;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getEntityVersion() {
        return entityVersion;
    }
}
