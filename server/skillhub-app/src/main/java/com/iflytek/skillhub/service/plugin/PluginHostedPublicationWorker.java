package com.iflytek.skillhub.service.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    private final com.iflytek.skillhub.repository.PluginPublicationWorkQueryRepository work;
    private final PluginPublicationCoordinator app;
    private final PluginChannelAttemptRunner runner;

    public PluginHostedPublicationWorker(
            com.iflytek.skillhub.repository.PluginPublicationWorkQueryRepository work,
            PluginPublicationCoordinator app,
            PluginChannelAttemptRunner runner) {
        this.runner = runner;
        this.work = work;
        this.app = app;
    }

    @Scheduled(fixedDelayString = "${skillhub.plugin.hosted-worker-delay-ms:5000}")
    public void publishQueuedHostedArtifacts() {
        for (var task : work.runnable(java.time.Instant.now())) {
            try {
                if (task.operationId() == null) app.execute(task.releaseId());
                else runner.executeGovernance(task.releaseId(), task.operationId());
            } catch (RuntimeException ex) {
                log.warn("Plugin hosted publication deferred for release {}", task.releaseId());
            }
        }
    }
}
