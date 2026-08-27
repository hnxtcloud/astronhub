package com.iflytek.skillhub.domain.plugin;

import java.util.List;
import java.util.Optional;

public interface PluginReleaseRepository {
    Optional<PluginRelease> findById(Long id);
    List<PluginRelease> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    PluginRelease save(PluginRelease release);
}
