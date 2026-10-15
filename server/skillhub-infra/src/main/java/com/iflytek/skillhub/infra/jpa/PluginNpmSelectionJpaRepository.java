package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginNpmSelection;
import com.iflytek.skillhub.domain.plugin.PluginNpmSelectionRepository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginNpmSelectionJpaRepository
        extends JpaRepository<PluginNpmSelection, Long>, PluginNpmSelectionRepository {}
