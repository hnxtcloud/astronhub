package com.iflytek.skillhub.domain.plugin;

/** Approval request order shared by current catalog selection and native market indexes. */
public final class PluginPublicationOrder {
    private PluginPublicationOrder() {}

    public static int compare(Long taskId, Long releaseId, Long otherTaskId, Long otherReleaseId) {
        int task =
                Long.compare(taskId == null ? -1 : taskId, otherTaskId == null ? -1 : otherTaskId);
        return task != 0
                ? task
                : Long.compare(
                        releaseId == null ? -1 : releaseId,
                        otherReleaseId == null ? -1 : otherReleaseId);
    }
}
