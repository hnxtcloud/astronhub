package com.iflytek.skillhub.domain.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Immutable remote intent, persisted before push and retained even if its executor disappears. */
@Entity
@Table(
        name = "plugin_git_publication_intent",
        uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "commit_sha"}))
public class PluginGitPublicationIntent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "parent_sha", length = 40)
    private String parentSha;

    @Column(name = "tree_sha", nullable = false, length = 40)
    private String treeSha;

    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    @Column(name = "plan_digest", nullable = false, length = 64)
    private String planDigest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PluginGitPublicationIntent() {}

    public PluginGitPublicationIntent(
            Long taskId, String parent, String tree, String commit, String digest) {
        if ((parent != null && !parent.matches("[a-f0-9]{40}"))
                || !tree.matches("[a-f0-9]{40}")
                || !commit.matches("[a-f0-9]{40}")
                || !digest.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("INVALID_GIT_INTENT");
        this.taskId = taskId;
        parentSha = parent;
        treeSha = tree;
        commitSha = commit;
        planDigest = digest;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getParentSha() {
        return parentSha;
    }

    public String getTreeSha() {
        return treeSha;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getPlanDigest() {
        return planDigest;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
