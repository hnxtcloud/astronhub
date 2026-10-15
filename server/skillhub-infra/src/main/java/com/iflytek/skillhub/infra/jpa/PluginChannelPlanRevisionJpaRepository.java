package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginChannelPlanRevisionJpaRepository
        extends JpaRepository<PluginChannelPlanRevision, Long>,
                PluginChannelPlanRevisionRepository {}
