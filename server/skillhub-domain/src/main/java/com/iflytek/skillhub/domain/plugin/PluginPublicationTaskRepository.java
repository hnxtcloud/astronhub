package com.iflytek.skillhub.domain.plugin;

import java.util.Optional;

public interface PluginPublicationTaskRepository {
    java.util.List<PluginPublicationTask> findRunnable(
            java.time.Instant now, org.springframework.data.domain.Pageable pageable);

    PluginPublicationTask save(PluginPublicationTask task);

    Optional<PluginPublicationTask> findByReleaseId(Long releaseId);

    java.util.List<PluginPublicationTask> findByStatusAndCompletedChannelsOrderByCreatedAtAsc(
            String status,
            String completedChannels,
            org.springframework.data.domain.Pageable pageable);
}
