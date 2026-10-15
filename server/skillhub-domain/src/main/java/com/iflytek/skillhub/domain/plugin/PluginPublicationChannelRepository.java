package com.iflytek.skillhub.domain.plugin;

import java.util.List;
import java.util.Optional;

public interface PluginPublicationChannelRepository {
    PluginPublicationChannel save(PluginPublicationChannel value);

    List<PluginPublicationChannel> findByTaskIdOrderByChannel(Long taskId);

    Optional<PluginPublicationChannel> findByIdForUpdate(Long id);
}
