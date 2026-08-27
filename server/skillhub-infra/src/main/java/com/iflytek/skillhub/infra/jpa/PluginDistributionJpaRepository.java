package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginDistribution;
import com.iflytek.skillhub.domain.plugin.PluginDistributionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PluginDistributionJpaRepository
        extends JpaRepository<PluginDistribution, Long>, PluginDistributionRepository {
}
