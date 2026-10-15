package com.iflytek.skillhub.service.plugin;

import com.iflytek.skillhub.domain.plugin.PluginDistributionRepository;
import com.iflytek.skillhub.domain.plugin.PluginPublicationChannel;
import com.iflytek.skillhub.domain.plugin.PluginPublicationChannelRepository;
import com.iflytek.skillhub.domain.plugin.PluginPublicationTask;
import com.iflytek.skillhub.domain.plugin.PluginPublicationTaskRepository;
import com.iflytek.skillhub.domain.plugin.PluginReleaseRepository;
import com.iflytek.skillhub.domain.plugin.service.PluginReleaseWorkflow;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

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
    private final PluginChannelAttemptRunner runner;
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
            com.iflytek.skillhub.domain.audit.AuditLogService audit,
            PluginChannelAttemptRunner runner) {
        this.audit = audit;
        this.runner = runner;
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
        for (String channel : task.getRequiredChannels().split(",")) {
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
        executeHosted(releaseId, false);
        task = tasks.findByReleaseId(releaseId).orElseThrow();
        if (!task.hasCompleted("HOSTED")) return;
        for (var channel : channels.findByTaskIdOrderByChannel(task.getId())) {
            if (channel.getChannel().equals("HOSTED")) continue;
            if (channel.getState().equals("SUCCEEDED")) {
                workflow.completeChannel(releaseId, channel.getChannel());
                continue;
            }
            var adapter = adapter(channel.getChannel());
            if (adapter == null) continue;
            runner.execute(releaseId, channel.getId(), adapter);
        }
    }

    public void executeHosted(Long releaseId, boolean retry) {
        Long id =
                tx.execute(
                        status -> {
                            var r = workflow.locked(releaseId);
                            var t = tasks.findByReleaseId(releaseId).orElseThrow();
                            if (t.hasCompleted("HOSTED")) return null;
                            var c =
                                    channels.findByTaskIdOrderByChannel(t.getId()).stream()
                                            .filter(v -> v.getChannel().equals("HOSTED"))
                                            .findFirst()
                                            .orElseGet(
                                                    () ->
                                                            channels.save(
                                                                    new PluginPublicationChannel(
                                                                            t.getId(),
                                                                            "HOSTED",
                                                                            adapter("HOSTED")
                                                                                    .snapshot(
                                                                                            workflow
                                                                                                    .requireProject(
                                                                                                            r
                                                                                                                    .getProjectId()),
                                                                                            r,
                                                                                            distributions
                                                                                                    .findByReleaseIdOrderByRuntimeKey(
                                                                                                            releaseId)))));
                            if (c.getState().equals("SUCCEEDED")) {
                                workflow.completeHosted(releaseId);
                                return null;
                            }
                            if (retry) {
                                c = channels.findByIdForUpdate(c.getId()).orElseThrow();
                                c.retryNow();
                                channels.save(c);
                            }
                            return c.getId();
                        });
        if (id != null) runner.execute(releaseId, id, adapter("HOSTED"));
    }
}
