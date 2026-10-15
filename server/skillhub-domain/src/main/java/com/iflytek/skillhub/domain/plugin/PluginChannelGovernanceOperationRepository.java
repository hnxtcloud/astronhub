package com.iflytek.skillhub.domain.plugin;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

public interface PluginChannelGovernanceOperationRepository {
    PluginChannelGovernanceOperation save(PluginChannelGovernanceOperation value);

    Optional<PluginChannelGovernanceOperation> findByIdForUpdate(Long id);

    List<PluginChannelGovernanceOperation> findByChannelIdOrderByIdDesc(Long channelId);

    List<PluginChannelGovernanceOperation> findRunnable(Instant now, Pageable page);
}
