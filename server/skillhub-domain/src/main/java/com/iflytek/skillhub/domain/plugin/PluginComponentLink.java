package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.*;

import java.time.Instant;

/** Non-owning, fixed-version reference. Target deletion never cascades plugin/package ownership. */
@Entity
@Table(
        name = "plugin_component_link",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "kind", "target_id"}))
public class PluginComponentLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PluginComponentLink() {}

    public PluginComponentLink(Long projectId, String kind, Long targetId, String actor) {
        this.projectId = projectId;
        this.kind = kind;
        this.targetId = targetId;
        this.createdBy = actor;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getKind() {
        return kind;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
