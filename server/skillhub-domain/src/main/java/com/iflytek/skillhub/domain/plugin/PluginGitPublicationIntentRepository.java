package com.iflytek.skillhub.domain.plugin;

public interface PluginGitPublicationIntentRepository {
    PluginGitPublicationIntent save(PluginGitPublicationIntent intent);

    java.util.List<PluginGitPublicationIntent> findByTaskIdOrderByIdDesc(Long taskId);

    java.util.Optional<PluginGitPublicationIntent> findByTaskIdAndCommitSha(
            Long taskId, String commitSha);
}
