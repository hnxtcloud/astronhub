package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginGitDestinationClaim;
import com.iflytek.skillhub.domain.plugin.PluginGitDestinationClaimRepository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginGitDestinationClaimJpaRepository
        extends JpaRepository<PluginGitDestinationClaim, String>,
                PluginGitDestinationClaimRepository {}
