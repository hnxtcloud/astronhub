package com.iflytek.skillhub.domain.mcp;

import jakarta.persistence.*;
import java.time.Instant;

/** Human review decision for an MCP entry revision, separate from the revision state itself. */
@Entity
@Table(name = "mcp_review_decision")
public class McpReviewDecision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "revision_id", nullable = false)
    private Long revisionId;
    @Column(name = "reviewer_id", nullable = false, length = 128)
    private String reviewerId;
    @Column(nullable = false, length = 32)
    private String decision;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected McpReviewDecision() {}

    public McpReviewDecision(Long revisionId, String reviewerId, String decision, String notes) {
        this.revisionId = revisionId;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.notes = notes;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getRevisionId() { return revisionId; }
    public String getReviewerId() { return reviewerId; }
    public String getDecision() { return decision; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
}
