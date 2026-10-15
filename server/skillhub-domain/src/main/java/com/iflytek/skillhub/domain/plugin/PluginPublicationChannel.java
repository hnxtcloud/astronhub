package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Immutable approved target plan and durable channel receipt; no secret material. */
@Entity
@Table(
        name = "plugin_publication_channel",
        uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "channel"}))
public class PluginPublicationChannel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(nullable = false, length = 16)
    private String channel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshot;

    @Column(nullable = false, length = 32)
    private String state = "QUEUED";

    @Column(name = "attempt_token", length = 36)
    private String attemptToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 80)
    private String lastError;

    @Column(columnDefinition = "TEXT")
    private String receipt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected PluginPublicationChannel() {}

    public PluginPublicationChannel(Long taskId, String channel, String snapshot) {
        this.taskId = taskId;
        this.channel = channel;
        this.snapshot = snapshot;
        if (snapshot == null || snapshot.equals("MISSING_CONFIGURATION")) {
            this.snapshot = "MISSING_CONFIGURATION";
            state = "BLOCKED";
            lastError = "MISSING_CONFIGURATION";
        }
    }

    public String claim(Instant now) {
        if (state.equals("SUCCEEDED")
                || state.equals("BLOCKED")
                || (state.equals("RUNNING") && leaseUntil.isAfter(now))) return null;
        attemptToken = UUID.randomUUID().toString();
        state = "RUNNING";
        leaseUntil = now.plusSeconds(300);
        attemptCount++;
        return attemptToken;
    }

    public boolean finish(String token, String result, String error) {
        if (!state.equals("RUNNING") || !java.util.Objects.equals(token, attemptToken))
            return false;
        if (error == null) {
            state = "SUCCEEDED";
            receipt = result;
            lastError = null;
        } else {
            state = "FAILED";
            lastError = error;
        }
        leaseUntil = null;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getChannel() {
        return channel;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public String getState() {
        return state;
    }

    public String getReceipt() {
        return receipt;
    }

    public String getLastError() {
        return lastError;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}
