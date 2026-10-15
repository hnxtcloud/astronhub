package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Explicit native withdrawal evidence, independent of the historical publication receipt. */
@Entity
@Table(
        name = "plugin_channel_governance_operation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "preview_digest"}))
public class PluginChannelGovernanceOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "release_id", nullable = false)
    private Long releaseId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "plan_revision", nullable = false)
    private int planRevision;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(nullable = false, length = 24)
    private String action = "WITHDRAW_CURRENT";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshot;

    @Column(name = "source_receipt", columnDefinition = "TEXT")
    private String sourceReceipt;

    @Column(name = "requested_by", nullable = false, length = 128)
    private String requestedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "preview_digest", nullable = false, length = 64)
    private String previewDigest;

    @Column(nullable = false, length = 24)
    private String state = "QUEUED";

    @Column(name = "attempt_token", length = 36)
    private String attemptToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(columnDefinition = "TEXT")
    private String receipt;

    @Column(name = "last_error", length = 80)
    private String lastError;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected PluginChannelGovernanceOperation() {}

    public PluginChannelGovernanceOperation(
            PluginPublicationChannel c,
            Long releaseId,
            String actor,
            String reason,
            String digest) {
        channelId = c.getId();
        this.releaseId = releaseId;
        taskId = c.getTaskId();
        channel = c.getChannel();
        planRevision = c.getPlanRevision();
        snapshot = c.getEffectiveSnapshot();
        sourceReceipt = c.getReceipt();
        requestedBy = actor;
        this.reason = reason;
        previewDigest = digest;
        createdAt = Instant.now();
    }

    public void unsupported() {
        state = "UNSUPPORTED";
        receipt = "{\"remote\":\"REMOTE_PACKAGE_RETAINED\",\"local\":\"SOURCE_DISABLED\"}";
    }

    public String claim(Instant now, int seconds) {
        if (!java.util.Set.of("QUEUED", "FAILED", "UNKNOWN", "RUNNING").contains(state)
                || (nextAttemptAt != null && nextAttemptAt.isAfter(now))
                || (state.equals("RUNNING") && leaseUntil != null && leaseUntil.isAfter(now)))
            return null;
        attemptToken = UUID.randomUUID().toString();
        state = "RUNNING";
        leaseUntil = now.plusSeconds(seconds);
        attemptCount++;
        return attemptToken;
    }

    public boolean owns(String token, Instant now) {
        return state.equals("RUNNING")
                && java.util.Objects.equals(token, attemptToken)
                && leaseUntil != null
                && leaseUntil.isAfter(now);
    }

    public boolean heartbeat(String token, Instant now, int seconds) {
        if (!owns(token, now)) return false;
        leaseUntil = now.plusSeconds(seconds);
        return true;
    }

    public boolean finish(String token, Instant now, String result, String error, boolean unknown) {
        if (!owns(token, now)) return false;
        receipt = result;
        lastError = error;
        state = error == null ? "SUCCEEDED" : unknown ? "UNKNOWN" : "FAILED";
        leaseUntil = null;
        nextAttemptAt =
                error == null
                        ? null
                        : now.plusSeconds(Math.min(300, Math.max(5, attemptCount * 5L)));
        return true;
    }

    public void retryNow() {
        if (!state.equals("RUNNING")) nextAttemptAt = null;
    }

    public Long getId() {
        return id;
    }

    public Long getChannelId() {
        return channelId;
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public int getPlanRevision() {
        return planRevision;
    }

    public String getChannel() {
        return channel;
    }

    public String getAction() {
        return action;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public String getSourceReceipt() {
        return sourceReceipt;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getReason() {
        return reason;
    }

    public String getPreviewDigest() {
        return previewDigest;
    }

    public String getState() {
        return state;
    }

    public String getAttemptToken() {
        return attemptToken;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getReceipt() {
        return receipt;
    }

    public String getLastError() {
        return lastError;
    }
}
