package com.iflytek.skillhub.domain.plugin;

import org.springframework.data.domain.Pageable;

import java.util.*;

public interface PluginChannelAttemptRepository {
    PluginChannelAttempt save(PluginChannelAttempt value);

    Optional<PluginChannelAttempt> findByAttemptToken(String token);

    List<PluginChannelAttempt> findByChannelIdOrderByIdDesc(Long channelId, Pageable page);

    long countByChannelId(Long channelId);
}
