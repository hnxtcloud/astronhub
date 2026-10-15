package com.iflytek.skillhub.infra.jpa;
import com.iflytek.skillhub.domain.plugin.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PluginPublicationTaskJpaRepository extends JpaRepository<PluginPublicationTask, Long>, PluginPublicationTaskRepository {}
