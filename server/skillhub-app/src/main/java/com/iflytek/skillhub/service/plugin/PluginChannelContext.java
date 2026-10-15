package com.iflytek.skillhub.service.plugin;

/** Persisted approved plan; adapters must use this snapshot, never current target settings. */
public record PluginChannelContext(Long taskId, Long releaseId, String channel, String snapshot) {}
