package com.iflytek.skillhub.domain.software;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;

/**
 * Logical download session: binds one user to one package revision so range
 * resume requests are not re-counted. It never replaces the per-request
 * permission and publication checks.
 */
@Entity
@Table(name = "software_download_session")
public class SoftwareDownloadSession {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "package_revision_id", nullable = false)
    private Long packageRevisionId;

    /** True once this session has been counted against the package's download counter. */
    @Column(nullable = false)
    private boolean counted;

    @Column(name = "idle_timeout_minutes", nullable = false)
    private int idleTimeoutMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SoftwareDownloadSession() {
    }

    public SoftwareDownloadSession(String id,
                                   String userId,
                                   Long packageRevisionId,
                                   boolean counted,
                                   int idleTimeoutMinutes,
                                   Instant now) {
        this.id = id;
        this.userId = userId;
        this.packageRevisionId = packageRevisionId;
        this.counted = counted;
        this.idleTimeoutMinutes = idleTimeoutMinutes;
        this.lastActivityAt = now;
        this.expiresAt = now.plusSeconds(idleTimeoutMinutes * 60L);
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

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Long getPackageRevisionId() {
        return packageRevisionId;
    }

    public boolean isCounted() {
        return counted;
    }

    public int getIdleTimeoutMinutes() {
        return idleTimeoutMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Extends the idle window; a new window only starts from real activity. */
    public void touch(Instant now) {
        this.lastActivityAt = now;
        this.expiresAt = now.plusSeconds(idleTimeoutMinutes * 60L);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** A session is only usable by the user it was issued to. */
    public void requireOwner(String userId) {
        if (!this.userId.equals(userId)) {
            throw new com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException(
                    "error.software.download.sessionNotFound", id);
        }
    }
}
