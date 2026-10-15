package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginGitPublicationIntent;
import com.iflytek.skillhub.domain.plugin.PluginGitPublicationIntentRepository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginGitPublicationIntentJpaRepository
        extends JpaRepository<PluginGitPublicationIntent, Long>,
                PluginGitPublicationIntentRepository {}
