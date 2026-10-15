package com.iflytek.skillhub.service.plugin;

import com.iflytek.skillhub.domain.plugin.PluginPublicationTaskRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Persistent queue polling also resumes unprocessed approved hosted releases after restart. */
@Component
@ConditionalOnProperty(
        name = "skillhub.plugin.hosted-worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PluginHostedPublicationWorker {
    private static final Logger log = LoggerFactory.getLogger(PluginHostedPublicationWorker.class);
    private final PluginPublicationTaskRepository tasks;
    private final PluginPublicationCoordinator app;

    public PluginHostedPublicationWorker(
            PluginPublicationTaskRepository tasks, PluginPublicationCoordinator app) {
        this.tasks = tasks;
        this.app = app;
    }

    @Scheduled(fixedDelayString = "${skillhub.plugin.hosted-worker-delay-ms:5000}")
    public void publishQueuedHostedArtifacts() {
        for (var task : tasks.findRunnable(java.time.Instant.now(), PageRequest.of(0, 20))) {
            try {
                app.execute(task.getReleaseId());
            } catch (RuntimeException ex) {
                log.warn("Plugin hosted publication deferred for release {}", task.getReleaseId());
            }
        }
    }
}
