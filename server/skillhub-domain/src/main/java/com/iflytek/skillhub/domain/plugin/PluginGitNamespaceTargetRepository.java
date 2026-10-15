package com.iflytek.skillhub.domain.plugin;

public interface PluginGitNamespaceTargetRepository {
    PluginGitNamespaceTarget save(PluginGitNamespaceTarget value);

    java.util.Optional<PluginGitNamespaceTarget> findById(Long namespaceId);
}
