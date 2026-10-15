package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.*;
import java.time.Instant;

/** Human review is separate from static evidence and publication execution. */
@Entity
@Table(name = "plugin_review_decision")
public class PluginReviewDecision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "release_id", nullable = false)
    private Long releaseId;
    @Column(name = "reviewer_id", nullable = false, length = 128)
    private String reviewerId;
    @Column(nullable = false, length = 32)
    private String decision;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String notes;
    @Column(name = "runtime_test_result", nullable = false, length = 32)
    private String runtimeTestResult;
    @Column(name = "test_environment", columnDefinition = "TEXT")
    private String testEnvironment;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    protected PluginReviewDecision() {}
    public PluginReviewDecision(Long releaseId, String reviewerId, String decision, String notes,
                                String runtimeTestResult, String testEnvironment) {
        this.releaseId = releaseId;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.notes = notes;
        this.runtimeTestResult = runtimeTestResult;
        this.testEnvironment = testEnvironment;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getReleaseId() { return releaseId; }
    public String getReviewerId() { return reviewerId; }
    public String getDecision() { return decision; }
    public String getNotes() { return notes; }
    public String getRuntimeTestResult() { return runtimeTestResult; }
    public String getTestEnvironment() { return testEnvironment; }
    public Instant getCreatedAt() { return createdAt; }
}
