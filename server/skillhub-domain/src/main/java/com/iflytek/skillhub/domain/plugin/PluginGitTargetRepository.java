package com.iflytek.skillhub.domain.plugin;

public interface PluginGitTargetRepository {
    PluginGitTarget save(PluginGitTarget target);

    java.util.Optional<PluginGitTarget> findById(Long id);

    java.util.List<PluginGitTarget> findTop100ByOrderByIdDesc();
}
