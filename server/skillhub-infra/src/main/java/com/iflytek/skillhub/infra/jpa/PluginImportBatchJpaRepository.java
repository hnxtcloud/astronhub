package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginImportBatchJpaRepository
        extends JpaRepository<PluginImportBatch, String>, PluginImportBatchRepository {}
