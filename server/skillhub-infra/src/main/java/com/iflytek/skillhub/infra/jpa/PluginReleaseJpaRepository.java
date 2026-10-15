package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginRelease;
import com.iflytek.skillhub.domain.plugin.PluginReleaseRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PluginReleaseJpaRepository
        extends JpaRepository<PluginRelease, Long>, PluginReleaseRepository {
    /** Native row locking serializes imports/review/publication on PostgreSQL and H2 without FOR NO KEY UPDATE. */
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM plugin_release WHERE id = :id FOR UPDATE", nativeQuery = true)
    java.util.Optional<PluginRelease> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
