package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.*;

import java.time.Instant;

/** Append-only selected target revisions. Revision zero is the unmodified approval evidence. */
@Entity
@Table(
        name = "plugin_channel_plan_revision",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "revision"}))
public class PluginChannelPlanRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(nullable = false)
    private int revision;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshot;

    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PluginChannelPlanRevision() {}

    public PluginChannelPlanRevision(
            Long channelId,
            int revision,
            String snapshot,
            String actor,
            String reason,
            Instant at) {
        this.channelId = channelId;
        this.revision = revision;
        this.snapshot = snapshot;
        requestedBy = actor;
        this.reason = reason;
        createdAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getChannelId() {
        return channelId;
    }

    public int getRevision() {
        return revision;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
