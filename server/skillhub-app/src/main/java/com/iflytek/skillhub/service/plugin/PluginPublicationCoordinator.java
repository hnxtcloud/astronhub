package com.iflytek.skillhub.service.plugin;

import com.iflytek.skillhub.domain.plugin.PluginDistributionRepository;
import com.iflytek.skillhub.domain.plugin.PluginPublicationChannel;
import com.iflytek.skillhub.domain.plugin.PluginPublicationChannelRepository;
import com.iflytek.skillhub.domain.plugin.PluginPublicationTask;
import com.iflytek.skillhub.domain.plugin.PluginPublicationTaskRepository;
import com.iflytek.skillhub.domain.plugin.PluginReleaseRepository;
import com.iflytek.skillhub.domain.plugin.PluginReleaseStatus;
import com.iflytek.skillhub.domain.plugin.PluginVisibility;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.plugin.service.PluginReleaseWorkflow;
import com.iflytek.skillhub.service.AuditRequestContext;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One queue, short transactional claims, durable immutable plans, external calls outside DB locks.
 */
@Service
public class PluginPublicationCoordinator {
    private final PluginPublicationTaskRepository tasks;
    private final PluginPublicationChannelRepository channels;
    private final PluginReleaseRepository releases;
    private final PluginDistributionRepository distributions;
    private final PluginReleaseWorkflow workflow;
    private final List<PluginPublicationChannelAdapter> adapters;
    private final ObjectProvider<PluginManagementAppService> hosted;
    private final TransactionTemplate tx;
    private final com.iflytek.skillhub.domain.audit.AuditLogService audit;

    public PluginPublicationCoordinator(
            PluginPublicationTaskRepository tasks,
            PluginPublicationChannelRepository channels,
            PluginReleaseRepository releases,
            PluginDistributionRepository distributions,
            PluginReleaseWorkflow workflow,
            List<PluginPublicationChannelAdapter> adapters,
            ObjectProvider<PluginManagementAppService> hosted,
            PlatformTransactionManager manager,
            com.iflytek.skillhub.domain.audit.AuditLogService audit) {
        this.audit = audit;
        this.tasks = tasks;
        this.channels = channels;
        this.releases = releases;
        this.distributions = distributions;
        this.workflow = workflow;
        this.adapters = adapters;
        this.hosted = hosted;
        tx = new TransactionTemplate(manager);
    }

    /**
     * Called inside approval transaction. Snapshot implementations must never perform network I/O.
     */
    public void prepare(PluginPublicationTask task) {
        var release = releases.findById(task.getReleaseId()).orElseThrow();
        var project = workflow.requireProject(release.getProjectId());
        var archives = distributions.findByReleaseIdOrderByRuntimeKey(release.getId());
        for (String channel : task.getRequiredChannels().split(","))
            if (!channel.equals("HOSTED")) {
                String snapshot = null;
                var adapter = adapter(channel);
                if (adapter != null)
                    try {
                        snapshot = adapter.snapshot(project, release, archives);
                    } catch (RuntimeException ex) {
                        snapshot = null;
                    }
                channels.save(new PluginPublicationChannel(task.getId(), channel, snapshot));
            }
    }

    private PluginPublicationChannelAdapter adapter(String channel) {
        return adapters.stream().filter(a -> a.channel().equals(channel)).findFirst().orElse(null);
    }

    public void execute(Long releaseId) {
        var task = tasks.findByReleaseId(releaseId).orElseThrow();
        if (!task.hasCompleted("HOSTED"))
            hosted.getObject()
                    .executeHosted(
                            releaseId,
                            new PluginActor(task.getApprovedBy(), Map.of(), Set.of()),
                            new AuditRequestContext(null, "plugin-publication-worker"));
        task = tasks.findByReleaseId(releaseId).orElseThrow();
        if (!task.hasCompleted("HOSTED")) return;
        for (var channel : channels.findByTaskIdOrderByChannel(task.getId())) {
            if (channel.getState().equals("SUCCEEDED")) {
                workflow.completeChannel(releaseId, channel.getChannel());
                continue;
            }
            var adapter = adapter(channel.getChannel());
            if (adapter == null) continue;
            String token =
                    tx.execute(
                            status -> {
                                var release = workflow.locked(releaseId);
                                var project = workflow.requireProject(release.getProjectId());
                                workflow.requireActive(project);
                                if (project.isHidden()
                                        || project.getVisibility() != PluginVisibility.PUBLIC
                                        || release.getStatus()
                                                != PluginReleaseStatus.PENDING_REVIEW) return null;
                                var locked =
                                        channels.findByIdForUpdate(channel.getId()).orElseThrow();
                                String claim = locked.claim(Instant.now());
                                channels.save(locked);
                                return claim;
                            });
            if (token == null) continue;
            String receipt = null, error = null;
            try {
                receipt =
                        adapter.publish(
                                new PluginChannelContext(
                                        task.getId(),
                                        releaseId,
                                        channel.getChannel(),
                                        channel.getSnapshot()));
            } catch (Exception ex) {
                error =
                        ex instanceof NpmRegistryPublisher.Failure failure
                                ? failure.code()
                                : ex instanceof GitMarketplacePublisher.Failure gitFailure ? gitFailure.code() : "CHANNEL_PUBLICATION_FAILED";
            }
            final String finalReceipt = receipt, finalError = error;
            boolean accepted =
                    Boolean.TRUE.equals(
                            tx.execute(
                                    status -> {
                                        var locked =
                                                channels.findByIdForUpdate(channel.getId())
                                                        .orElseThrow();
                                        boolean done =
                                                locked.finish(token, finalReceipt, finalError);
                                        channels.save(locked);
                                        if (done)
                                            audit.record(
                                                    null,
                                                    finalError == null
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
            if (accepted && error == null)
                workflow.completeChannel(releaseId, channel.getChannel());
            else if (accepted)
                tx.executeWithoutResult(
                        status -> {
                            var locked = workflow.locked(releaseId);
                            var failed = tasks.findByReleaseId(locked.getId()).orElseThrow();
                            failed.failChannel("error.plugin.channelFailed");
                            tasks.save(failed);
                        });
        }
    }
}
