package com.iflytek.skillhub.domain.software;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Singleton pointer row selecting the active software policy revision. The id
 * is always {@link #SINGLETON_ID} (not generated): the row is created once by
 * the seed migration, and the pointer switch and the new revision insert share
 * one short transaction, giving readers an atomic view of the policy.
 */
@Entity
@Table(name = "software_policy_state")
public class SoftwarePolicyState {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "active_revision_id", nullable = false)
    private Long activeRevisionId;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long entityVersion;

    protected SoftwarePolicyState() {
    }

    public SoftwarePolicyState(Long activeRevisionId) {
        this.id = SINGLETON_ID;
        this.activeRevisionId = activeRevisionId;
    }

    public Long getId() {
        return id;
    }

    public Long getActiveRevisionId() {
        return activeRevisionId;
    }

    public void setActiveRevisionId(Long activeRevisionId) {
        this.activeRevisionId = activeRevisionId;
    }

    public long getEntityVersion() {
        return entityVersion;
    }
}
