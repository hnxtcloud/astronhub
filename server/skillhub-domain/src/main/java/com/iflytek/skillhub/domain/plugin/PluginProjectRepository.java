package com.iflytek.skillhub.domain.plugin;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PluginProjectRepository {
    Optional<PluginProject> findById(Long id);
    Optional<PluginProject> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    Page<PluginProject> findByStatusAndHiddenFalse(PluginProjectStatus status, Pageable pageable);
    PluginProject save(PluginProject project);
}
