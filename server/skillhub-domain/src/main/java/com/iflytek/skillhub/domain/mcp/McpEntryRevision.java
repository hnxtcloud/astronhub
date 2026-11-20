package com.iflytek.skillhub.domain.mcp;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "mcp_entry_revision")
public class McpEntryRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "entry_id", nullable = false)
    private Long entryId;
    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private McpRevisionStatus status;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "source_kind", nullable = false, length = 32)
    private String sourceKind;
    @Column(name = "source_snapshot", columnDefinition = "TEXT")
    private String sourceSnapshot;
    @Column(name = "local_curation", columnDefinition = "TEXT")
    private String localCuration;
    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected McpEntryRevision() {}

    public McpEntryRevision(Long entryId, int revisionNumber, String sourceKind, String createdBy) {
        this.entryId = entryId;
        this.revisionNumber = revisionNumber;
        this.sourceKind = sourceKind;
        this.createdBy = createdBy;
        this.status = McpRevisionStatus.DRAFT;
    }

    @PrePersist
    protected void onCreate() { createdAt = Instant.now(Clock.systemUTC()); }

    public Long getId() { return id; }
    public Long getEntryId() { return entryId; }
    public int getRevisionNumber() { return revisionNumber; }
    public McpRevisionStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getSourceKind() { return sourceKind; }
    public String getSourceSnapshot() { return sourceSnapshot; }
    public String getLocalCuration() { return localCuration; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }

    /** Draft revisions are the only mutable revisions; published revisions stay immutable. */
    public void requireEditable() {
        if (status != McpRevisionStatus.DRAFT) {
            throw new DomainBadRequestException("error.mcp.notEditable");
        }
    }

    public void requireDeletable() {
        if (status != McpRevisionStatus.DRAFT && status != McpRevisionStatus.REJECTED) {
            throw new DomainBadRequestException("error.mcp.notDeletable");
        }
    }

    public void submit() {
        if (status != McpRevisionStatus.DRAFT) {
            throw new DomainBadRequestException("error.mcp.reviewState");
        }
        status = McpRevisionStatus.PENDING_REVIEW;
    }

    public void withdraw() {
        if (status != McpRevisionStatus.PENDING_REVIEW) {
            throw new DomainBadRequestException("error.mcp.reviewState");
        }
        status = McpRevisionStatus.DRAFT;
    }

    public void approve() {
        if (status != McpRevisionStatus.PENDING_REVIEW) {
            throw new DomainBadRequestException("error.mcp.reviewState");
        }
        status = McpRevisionStatus.PUBLISHED;
        publishedAt = Instant.now(Clock.systemUTC());
    }

    public void reject() {
        if (status != McpRevisionStatus.PENDING_REVIEW) {
            throw new DomainBadRequestException("error.mcp.reviewState");
        }
        status = McpRevisionStatus.REJECTED;
    }

    public void updateDraft(String description, String localCuration) {
        requireEditable();
        this.description = description;
        this.localCuration = localCuration;
    }

    /** Provenance snapshot (manual metadata or imported server.json) attaches to drafts only. */
    public void attachSourceSnapshot(String sourceSnapshot) {
        requireEditable();
        this.sourceSnapshot = sourceSnapshot;
    }
}
