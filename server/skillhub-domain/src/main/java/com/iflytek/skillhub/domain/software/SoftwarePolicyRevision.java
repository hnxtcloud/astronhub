package com.iflytek.skillhub.domain.software;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

/**
 * An immutable revision of the software center runtime policy. Configurations
 * are typed values serialized as JSON with a schema version; the active
 * revision is selected by the single-row {@link SoftwarePolicyState} pointer.
 */
@Entity
@Table(name = "software_policy_revision")
public class SoftwarePolicyRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private long revision;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String config;

    @Column(name = "changed_by", nullable = false, length = 128)
    private String changedBy;

    @Column(name = "change_reason", nullable = false, length = 512)
    private String changeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SoftwarePolicyRevision() {
    }

    public SoftwarePolicyRevision(long revision, int schemaVersion, String config,
                                  String changedBy, String changeReason) {
        this.revision = revision;
        this.schemaVersion = schemaVersion;
        this.config = config;
        this.changedBy = changedBy;
        this.changeReason = changeReason;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() {
        return id;
    }

    public long getRevision() {
        return revision;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getConfig() {
        return config;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
