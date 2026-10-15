package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginGitNamespaceTarget;
import com.iflytek.skillhub.domain.plugin.PluginGitNamespaceTargetRepository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginGitNamespaceTargetJpaRepository
        extends JpaRepository<PluginGitNamespaceTarget, Long>, PluginGitNamespaceTargetRepository {}
