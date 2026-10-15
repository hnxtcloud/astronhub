package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginPublicationChannel;
import com.iflytek.skillhub.domain.plugin.PluginPublicationChannelRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PluginPublicationChannelJpaRepository
        extends JpaRepository<PluginPublicationChannel, Long>, PluginPublicationChannelRepository {
    @Query(
            value = "SELECT * FROM plugin_publication_channel WHERE id=:id FOR UPDATE",
            nativeQuery = true)
    Optional<PluginPublicationChannel> findByIdForUpdate(Long id);
}
