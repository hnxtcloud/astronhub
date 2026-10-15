package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginGitTarget;
import com.iflytek.skillhub.domain.plugin.PluginGitTargetRepository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginGitTargetJpaRepository
        extends JpaRepository<PluginGitTarget, Long>, PluginGitTargetRepository {}
