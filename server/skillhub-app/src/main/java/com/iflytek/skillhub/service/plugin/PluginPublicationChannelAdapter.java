package com.iflytek.skillhub.service.plugin;

import com.iflytek.skillhub.domain.plugin.PluginDistribution;
import com.iflytek.skillhub.domain.plugin.PluginProject;
import com.iflytek.skillhub.domain.plugin.PluginRelease;

import java.util.List;

/** No network in snapshot. Null/missing configuration is permanently blocked for this plan. */
public interface PluginPublicationChannelAdapter {
    String channel();

    String snapshot(
            PluginProject project, PluginRelease release, List<PluginDistribution> distributions);

    String publish(PluginChannelContext context) throws Exception;
}
