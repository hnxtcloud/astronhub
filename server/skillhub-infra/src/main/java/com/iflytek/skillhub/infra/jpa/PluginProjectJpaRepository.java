package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginProject;
import com.iflytek.skillhub.domain.plugin.PluginProjectRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PluginProjectJpaRepository
        extends JpaRepository<PluginProject, Long>, PluginProjectRepository {
}
