package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.PluginProject;
import com.iflytek.skillhub.domain.plugin.PluginProjectRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PluginProjectJpaRepository
        extends JpaRepository<PluginProject, Long>, PluginProjectRepository {
    /** Native row locking preserves PostgreSQL/H2 semantics; Hibernate emits H2-incompatible FOR NO KEY UPDATE. */
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM plugin_project WHERE id = :id FOR UPDATE", nativeQuery = true)
    java.util.Optional<PluginProject> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
