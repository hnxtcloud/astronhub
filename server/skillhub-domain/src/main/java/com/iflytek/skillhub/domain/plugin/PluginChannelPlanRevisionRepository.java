package com.iflytek.skillhub.domain.plugin;

import java.util.List;

public interface PluginChannelPlanRevisionRepository {
    List<PluginChannelPlanRevision> findByChannelIdOrderByRevisionAsc(Long channelId);

    PluginChannelPlanRevision save(PluginChannelPlanRevision value);
}
