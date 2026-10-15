package com.iflytek.skillhub.domain.plugin;

public interface PluginGitDestinationClaimRepository {
    PluginGitDestinationClaim save(PluginGitDestinationClaim value);

    java.util.Optional<PluginGitDestinationClaim> findById(String key);
}
