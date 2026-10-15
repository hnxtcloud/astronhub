package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.*;

import java.time.Instant;

/** Durable attempt evidence; a late observation never overwrites the owned attempt result. */
@Entity
@Table(
        name = "plugin_channel_attempt",
        uniqueConstraints = @UniqueConstraint(columnNames = "attempt_token"))
public class PluginChannelAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "operation_id")
    private Long operationId;

    @Column(name = "plan_revision", nullable = false)
    private int planRevision;

    @Column(name = "attempt_token", nullable = false, length = 36)
    private String attemptToken;

    @Column(nullable = false, length = 24)
    private String action;

    @Column(nullable = false, length = 24)
    private String outcome = "RUNNING";

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;

    @Column(name = "lease_until", nullable = false)
    private Instant leaseUntil;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(columnDefinition = "TEXT")
    private String receipt;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "late_receipt", columnDefinition = "TEXT")
    private String lateReceipt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected PluginChannelAttempt() {}

    public PluginChannelAttempt(
            Long channel, int revision, String token, String action, Instant now, Instant lease) {
        channelId = channel;
        planRevision = revision;
        attemptToken = token;
        this.action = action;
        startedAt = now;
        heartbeatAt = now;
        leaseUntil = lease;
    }

    public void heartbeat(Instant now, Instant lease) {
        if (outcome.equals("RUNNING")) {
            heartbeatAt = now;
            leaseUntil = lease;
        }
    }

    public void forOperation(Long operationId) {
        this.operationId = operationId;
    }

    public Long getOperationId() {
        return operationId;
    }

    public void finish(String outcome, String receipt, String error, Instant now) {
        if (!this.outcome.equals("RUNNING")) {
            if (lateReceipt == null && receipt != null) lateReceipt = receipt;
            return;
        }
        this.outcome = outcome;
        this.receipt = receipt;
        errorCode = error;
        finishedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getChannelId() {
        return channelId;
    }

    public int getPlanRevision() {
        return planRevision;
    }

    public String getAttemptToken() {
        return attemptToken;
    }

    public String getAction() {
        return action;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getReceipt() {
        return receipt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getLateReceipt() {
        return lateReceipt;
    }
}
