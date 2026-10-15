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

    default String publish(PluginChannelContext context, Runnable beforeWrite) throws Exception {
        try (var binding = PluginPublicationWriteGuard.bind(beforeWrite)) {
            beforeWrite.run();
            return publish(context);
        }
    }

    /** Read-only reconciliation. ABSENT permits a later original-plan retry, never this read. */
    default Inspection reconcile(PluginChannelContext context) throws Exception {
        return new Inspection("UNKNOWN", null, "RECONCILIATION_UNAVAILABLE");
    }

    record Inspection(String state, String receipt, String reason) {}
}
