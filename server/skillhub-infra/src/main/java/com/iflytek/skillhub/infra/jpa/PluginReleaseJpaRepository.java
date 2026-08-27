package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginRelease;
import com.iflytek.skillhub.domain.plugin.PluginReleaseRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PluginReleaseJpaRepository
        extends JpaRepository<PluginRelease, Long>, PluginReleaseRepository {
}
