package com.iflytek.skillhub.domain.plugin;

import java.util.List;

public interface PluginDistributionRepository {
    List<PluginDistribution> findByReleaseIdOrderByRuntimeKey(Long releaseId);
    PluginDistribution save(PluginDistribution distribution);
}
