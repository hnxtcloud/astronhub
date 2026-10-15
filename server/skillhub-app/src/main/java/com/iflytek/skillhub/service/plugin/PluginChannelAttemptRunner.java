package com.iflytek.skillhub.service.plugin;

import com.iflytek.skillhub.config.PluginPublicationProperties;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.plugin.service.PluginReleaseWorkflow;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** One execution boundary for short claims, independent heartbeats, receipts and stale fencing. */
@Service
public class PluginChannelAttemptRunner {
    private final PluginChannelGovernanceOperationRepository operations;
    private final PluginGitPublicationAdapter git;
    private final PluginPublicationChannelRepository channels;
    private final PluginChannelAttemptRepository attempts;
    private final PluginChannelPlanRevisionRepository plans;
    private final PluginPublicationTaskRepository tasks;
    private final PluginReleaseWorkflow workflow;
    private final PluginProjectRepository projects;
    private final PluginPublicationProperties properties;
    private final TransactionTemplate tx;
    private final AuditLogService audit;
    private final ScheduledExecutorService heartbeats =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        var t = new Thread(r, "plugin-publication-heartbeat");
                        t.setDaemon(true);
                        return t;
                    });

    public PluginChannelAttemptRunner(
            PluginPublicationChannelRepository channels,
            PluginChannelAttemptRepository attempts,
            PluginChannelPlanRevisionRepository plans,
            PluginPublicationTaskRepository tasks,
            PluginReleaseWorkflow workflow,
            PluginPublicationProperties properties,
            PlatformTransactionManager manager,
            AuditLogService audit,
            PluginChannelGovernanceOperationRepository operations,
            PluginGitPublicationAdapter git,
            PluginProjectRepository projects) {
        this.projects = projects;
        this.operations = operations;
        this.git = git;
        this.channels = channels;
        this.attempts = attempts;
        this.plans = plans;
        this.tasks = tasks;
        this.workflow = workflow;
        this.properties = properties;
        this.audit = audit;
        tx = new TransactionTemplate(manager);
    }

    @PreDestroy
    void shutdown() {
        heartbeats.shutdownNow();
    }

    private record GovernanceLease(
            Long id,
            Long channelId,
            Long taskId,
            Long releaseId,
            int revision,
            String token,
            String snapshot,
            boolean reconcile) {}

    public void executeGovernance(Long releaseId, Long operationId) {
        var lease =
                tx.execute(
                        status -> {
                            workflow.locked(releaseId);
                            var op = operations.findByIdForUpdate(operationId).orElseThrow();
                            if (!op.getReleaseId().equals(releaseId)
                                    || !op.getChannel().equals("GIT")) return null;
                            var now = Instant.now();
                            boolean reconcile =
                                    op.getState().equals("UNKNOWN")
                                            || (op.getState().equals("RUNNING")
                                                    && op.getLeaseUntil() != null
                                                    && !op.getLeaseUntil().isAfter(now));
                            if (reconcile && op.getAttemptToken() != null)
                                attempts.findByAttemptToken(op.getAttemptToken())
                                        .ifPresent(
                                                a -> {
                                                    a.finish("EXPIRED", null, "LEASE_EXPIRED", now);
                                                    attempts.save(a);
                                                });
                            var token = op.claim(now, properties.getLeaseSeconds());
                            if (token == null) return null;
                            operations.save(op);
                            var attempt =
                                    new PluginChannelAttempt(
                                            op.getChannelId(),
                                            op.getPlanRevision(),
                                            token,
                                            reconcile ? "RECONCILE_WITHDRAW" : "WITHDRAW_CURRENT",
                                            now,
                                            op.getLeaseUntil());
                            attempt.forOperation(op.getId());
                            attempts.save(attempt);
                            return new GovernanceLease(
                                    op.getId(),
                                    op.getChannelId(),
                                    op.getTaskId(),
                                    releaseId,
                                    op.getPlanRevision(),
                                    token,
                                    op.getSnapshot(),
                                    reconcile);
                        });
        if (lease == null) return;
        var pulse =
                heartbeats.scheduleAtFixedRate(
                        () -> {
                            try {
                                tx.executeWithoutResult(
                                        status -> {
                                            var op =
                                                    operations
                                                            .findByIdForUpdate(lease.id)
                                                            .orElseThrow();
                                            var now = Instant.now();
                                            if (op.heartbeat(
                                                    lease.token,
                                                    now,
                                                    properties.getLeaseSeconds())) {
                                                operations.save(op);
                                                attempts.findByAttemptToken(lease.token)
                                                        .ifPresent(
                                                                a -> {
                                                                    a.heartbeat(
                                                                            now,
                                                                            op.getLeaseUntil());
                                                                    attempts.save(a);
                                                                });
                                            }
                                        });
                            } catch (RuntimeException ignored) {
                            }
                        },
                        properties.getHeartbeatMillis(),
                        properties.getHeartbeatMillis(),
                        TimeUnit.MILLISECONDS);
        String receipt = null, error = null;
        boolean unknown = false;
        try {
            var result =
                    git.withdraw(
                            new PluginChannelContext(
                                    lease.taskId, releaseId, "GIT", lease.snapshot),
                            lease.id,
                            lease.reconcile,
                            () -> {
                                if (!Boolean.TRUE.equals(
                                        tx.execute(
                                                status ->
                                                        operations
                                                                .findByIdForUpdate(lease.id)
                                                                .orElseThrow()
                                                                .owns(lease.token, Instant.now()))))
                                    throw new IllegalStateException("PUBLICATION_LEASE_LOST");
                            });
            if (result.isPresent()) receipt = result.get();
            else error = "REMOTE_WITHDRAWAL_ABSENT";
        } catch (Exception ex) {
            error =
                    ex instanceof GitMarketplacePublisher.Failure f
                            ? f.code()
                            : "GIT_WITHDRAWAL_UNCONFIRMED";
            unknown =
                    lease.reconcile
                            || !Set.of("GIT_METADATA_REJECTED", "GIT_INVALID_PLAN").contains(error);
        } finally {
            pulse.cancel(false);
        }
        final String result = receipt, problem = error;
        final boolean uncertain = unknown;
        tx.executeWithoutResult(
                status -> {
                    var op = operations.findByIdForUpdate(lease.id).orElseThrow();
                    var now = Instant.now();
                    boolean accepted = op.finish(lease.token, now, result, problem, uncertain);
                    operations.save(op);
                    var attempt = attempts.findByAttemptToken(lease.token).orElseThrow();
                    attempt.finish(
                            accepted
                                    ? (problem == null
                                            ? "SUCCEEDED"
                                            : uncertain ? "UNKNOWN" : "FAILED")
                                    : "EXPIRED",
                            result,
                            accepted ? problem : "LEASE_LOST",
                            now);
                    attempts.save(attempt);
                    if (accepted)
                        audit.record(
                                null,
                                problem == null
                                        ? "plugin.channel.withdraw.succeeded"
                                        : "plugin.channel.withdraw.failed",
                                "PLUGIN_RELEASE",
                                releaseId,
                                null,
                                null,
                                "plugin-publication-worker",
                                "{}");
                });
    }

    private record Lease(
            Long channelId,
            Long taskId,
            Long releaseId,
            String channel,
            int revision,
            String token,
            String snapshot,
            boolean reconcile) {}

    private Lease claim(Long releaseId, Long channelId) {
        return tx.execute(
                status -> {
                    var r = workflow.locked(releaseId);
                    var p = projects.findByIdForUpdate(r.getProjectId()).orElseThrow();
                    workflow.requireActive(p);
                    var c = channels.findByIdForUpdate(channelId).orElseThrow();
                    if (p.isHidden()
                            || (!c.getChannel().equals("HOSTED")
                                    && p.getVisibility() != PluginVisibility.PUBLIC)
                            || r.getStatus() != PluginReleaseStatus.PENDING_REVIEW) return null;
                    var now = Instant.now();
                    boolean reconcile =
                            c.getState().equals("UNKNOWN")
                                    || (c.getState().equals("RUNNING")
                                            && c.getLeaseUntil() != null
                                            && !c.getLeaseUntil().isAfter(now));
                    if (c.getState().equals("RUNNING") && reconcile && c.getAttemptToken() != null)
                        attempts.findByAttemptToken(c.getAttemptToken())
                                .ifPresent(
                                        a -> {
                                            a.finish("EXPIRED", null, "LEASE_EXPIRED", now);
                                            attempts.save(a);
                                        });
                    String token = c.claim(now, properties.getLeaseSeconds());
                    if (token == null) return null;
                    channels.save(c);
                    var task = tasks.findByReleaseId(releaseId).orElseThrow();
                    if (plans.findByChannelIdOrderByRevisionAsc(c.getId()).isEmpty())
                        plans.save(
                                new PluginChannelPlanRevision(
                                        c.getId(),
                                        0,
                                        c.getSnapshot(),
                                        task.getApprovedBy(),
                                        "ORIGINAL_APPROVAL",
                                        task.getCreatedAt()));
                    attempts.save(
                            new PluginChannelAttempt(
                                    c.getId(),
                                    c.getPlanRevision(),
                                    token,
                                    reconcile ? "RECONCILE" : "PUBLISH",
                                    now,
                                    c.getLeaseUntil()));
                    return new Lease(
                            c.getId(),
                            task.getId(),
                            releaseId,
                            c.getChannel(),
                            c.getPlanRevision(),
                            token,
                            c.getEffectiveSnapshot(),
                            reconcile);
                });
    }

    private boolean heartbeat(Lease lease) {
        return Boolean.TRUE.equals(
                tx.execute(
                        status -> {
                            var c = channels.findByIdForUpdate(lease.channelId).orElseThrow();
                            var now = Instant.now();
                            if (!c.heartbeat(
                                    lease.token, lease.revision, now, properties.getLeaseSeconds()))
                                return false;
                            channels.save(c);
                            attempts.findByAttemptToken(lease.token)
                                    .ifPresent(
                                            a -> {
                                                a.heartbeat(now, c.getLeaseUntil());
                                                attempts.save(a);
                                            });
                            return true;
                        }));
    }

    private void beforeWrite(Lease lease) {
        Boolean owned =
                tx.execute(
                        status -> {
                            var r = workflow.locked(lease.releaseId);
                            var p = projects.findByIdForUpdate(r.getProjectId()).orElseThrow();
                            workflow.requireActive(p);
                            var c = channels.findByIdForUpdate(lease.channelId).orElseThrow();
                            return !p.isHidden()
                                    && (lease.channel.equals("HOSTED")
                                            || p.getVisibility() == PluginVisibility.PUBLIC)
                                    && r.getStatus() == PluginReleaseStatus.PENDING_REVIEW
                                    && c.owns(lease.token, lease.revision, Instant.now())
                                    && !c.isSourceDisabled();
                        });
        if (!Boolean.TRUE.equals(owned)) throw new IllegalStateException("PUBLICATION_LEASE_LOST");
    }

    public void execute(Long releaseId, Long channelId, PluginPublicationChannelAdapter adapter) {
        var lease = claim(releaseId, channelId);
        if (lease == null) return;
        var pulse =
                heartbeats.scheduleAtFixedRate(
                        () -> {
                            try {
                                heartbeat(lease);
                            } catch (RuntimeException ignored) {
                            }
                        },
                        properties.getHeartbeatMillis(),
                        properties.getHeartbeatMillis(),
                        TimeUnit.MILLISECONDS);
        String receipt = null, error = null;
        boolean unknown = false;
        try {
            var context =
                    new PluginChannelContext(
                            lease.taskId, lease.releaseId, lease.channel, lease.snapshot);
            if (lease.reconcile) {
                var observed = adapter.reconcile(context);
                if (observed.state().equals("PRESENT")) receipt = observed.receipt();
                else {
                    error = observed.reason() == null ? "REMOTE_CONTENT_ABSENT" : observed.reason();
                    unknown = !observed.state().equals("ABSENT");
                }
            } else receipt = adapter.publish(context, () -> beforeWrite(lease));
            if (receipt == null && error == null) {
                error = "REMOTE_RESULT_UNCONFIRMED";
                unknown = true;
            }
        } catch (Exception ex) {
            error =
                    lease.channel.equals("HOSTED")
                            ? "HOSTED_INTEGRITY_FAILED"
                            : ex instanceof NpmRegistryPublisher.Failure n
                                    ? n.code()
                                    : ex instanceof GitMarketplacePublisher.Failure g
                                            ? g.code()
                                            : "CHANNEL_PUBLICATION_UNCONFIRMED";
            unknown =
                    !lease.channel.equals("HOSTED")
                            && (lease.reconcile
                                    || !Set.of(
                                                    "PACKAGE_DIGEST_MISMATCH",
                                                    "PACKAGE_SIZE_LIMIT",
                                                    "PACKAGE_IDENTITY",
                                                    "REGISTRY_AUTHENTICATION",
                                                    "REGISTRY_VERSION_CONFLICT",
                                                    "GIT_ARCHIVE_DIGEST",
                                                    "GIT_METADATA_REJECTED",
                                                    "GIT_INVALID_PLAN",
                                                    "GIT_PUBLICATION_WITHDRAWN")
                                            .contains(error));
        } finally {
            pulse.cancel(false);
        }
        final String result = receipt, problem = error;
        final boolean uncertain = unknown;
        boolean accepted =
                Boolean.TRUE.equals(
                        tx.execute(
                                status -> {
                                    var c =
                                            channels.findByIdForUpdate(lease.channelId)
                                                    .orElseThrow();
                                    var now = Instant.now();
                                    boolean done =
                                            c.finish(
                                                    lease.token,
                                                    lease.revision,
                                                    now,
                                                    result,
                                                    problem,
                                                    uncertain);
                                    channels.save(c);
                                    var a = attempts.findByAttemptToken(lease.token).orElseThrow();
                                    a.finish(
                                            done
                                                    ? (problem == null
                                                            ? "SUCCEEDED"
                                                            : uncertain ? "UNKNOWN" : "FAILED")
                                                    : "EXPIRED",
                                            result,
                                            done ? problem : "LEASE_LOST",
                                            now);
                                    attempts.save(a);
                                    if (done)
                                        audit.record(
                                                null,
                                                problem == null
                                                        ? "plugin.channel.published"
                                                        : "plugin.channel.failed",
                                                "PLUGIN_RELEASE",
                                                releaseId,
                                                null,
                                                null,
                                                "plugin-publication-worker",
                                                "{}");
                                    return done;
                                }));
        if (accepted && error == null) workflow.completeChannel(releaseId, lease.channel);
        else if (accepted)
            tx.executeWithoutResult(
                    status -> {
                        var r = workflow.locked(releaseId);
                        var t = tasks.findByReleaseId(r.getId()).orElseThrow();
                        t.failChannel("error.plugin.channelFailed");
                        tasks.save(t);
                    });
    }
}
