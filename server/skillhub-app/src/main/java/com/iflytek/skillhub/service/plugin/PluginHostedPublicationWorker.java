package com.iflytek.skillhub.service.plugin;

import com.iflytek.skillhub.domain.plugin.PluginPublicationTaskRepository;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.service.AuditRequestContext;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Persistent queue polling also resumes unprocessed approved hosted releases after restart. */
@Component
@ConditionalOnProperty(name = "skillhub.plugin.hosted-worker-enabled", havingValue = "true", matchIfMissing = true)
public class PluginHostedPublicationWorker {
    private static final Logger log = LoggerFactory.getLogger(PluginHostedPublicationWorker.class);
    private final PluginPublicationTaskRepository tasks;
    private final PluginManagementAppService app;
    public PluginHostedPublicationWorker(PluginPublicationTaskRepository tasks, PluginManagementAppService app) {
        this.tasks = tasks; this.app = app;
    }
    @Scheduled(fixedDelayString = "${skillhub.plugin.hosted-worker-delay-ms:5000}")
    public void publishQueuedHostedArtifacts() {
        for (var task : tasks.findByStatusAndCompletedChannelsOrderByCreatedAtAsc("QUEUED", "", PageRequest.of(0, 20))) {
            try {
                app.executeHosted(task.getReleaseId(), new PluginActor(task.getApprovedBy(), Map.of(), Set.of()),
                        new AuditRequestContext(null, "plugin-hosted-worker"));
            } catch (RuntimeException ex) {
                log.warn("Plugin hosted publication deferred for release {}", task.getReleaseId());
            }
        }
    }
}
