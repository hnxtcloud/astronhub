package com.iflytek.skillhub.domain.plugin;

import java.util.*;

public interface PluginComponentLinkRepository {
    List<PluginComponentLink> findByProjectIdOrderByIdAsc(Long projectId);

    Optional<PluginComponentLink> findById(Long id);

    Optional<PluginComponentLink> findByProjectIdAndKindAndTargetId(
            Long projectId, String kind, Long targetId);

    PluginComponentLink save(PluginComponentLink link);

    void delete(PluginComponentLink link);
}
