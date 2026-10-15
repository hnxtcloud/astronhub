package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.*;
import java.time.Instant;

/** Durable approved-content snapshot. Channel workers may only finish their own channel. */
@Entity
@Table(name = "plugin_publication_task", uniqueConstraints = @UniqueConstraint(columnNames = "release_id"))
public class PluginPublicationTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "release_id", nullable = false)
    private Long releaseId;
    @Column(name = "approved_by", nullable = false, length = 128)
    private String approvedBy;
    @Column(name = "required_channels", nullable = false, length = 200)
    private String requiredChannels;
    @Column(name = "completed_channels", nullable = false, length = 200)
    private String completedChannels = "";
    @Column(nullable = false, length = 32)
    private String status = "QUEUED";
    @Column(name = "content_snapshot", nullable = false, columnDefinition = "TEXT")
    private String contentSnapshot;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "last_error", length = 200)
    private String lastError;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Version @Column(name = "row_version", nullable = false)
    private long rowVersion;
    protected PluginPublicationTask() {}
    public PluginPublicationTask(Long releaseId, String approvedBy, String requiredChannels, String snapshot) {
        this.releaseId = releaseId;
        this.approvedBy = approvedBy;
        this.requiredChannels = requiredChannels;
        this.contentSnapshot = snapshot;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public Long getReleaseId() { return releaseId; }
    public String getApprovedBy() { return approvedBy; }
    public String getRequiredChannels() { return requiredChannels; }
    public String getCompletedChannels() { return completedChannels; }
    public String getStatus() { return status; }
    public String getContentSnapshot() { return contentSnapshot; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getLastError() { return lastError; }
    public int getAttemptCount() { return attemptCount; }
    public void failHosted() { status = "FAILED"; lastError = "error.plugin.hostedIntegrity"; attemptCount++; }
    public void completeHosted() {
        attemptCount++;
        lastError = null;
        status = "QUEUED";
        completedChannels = "HOSTED";
        if ("HOSTED".equals(requiredChannels)) {
            status = "SUCCEEDED";
            completedAt = Instant.now();
        }
    }
}
