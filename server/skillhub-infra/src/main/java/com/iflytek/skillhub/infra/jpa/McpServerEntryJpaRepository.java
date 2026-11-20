package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.mcp.McpServerEntry;
import com.iflytek.skillhub.domain.mcp.McpServerEntryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface McpServerEntryJpaRepository
        extends JpaRepository<McpServerEntry, Long>, McpServerEntryRepository {
    /** Native row locking preserves PostgreSQL/H2 semantics; Hibernate emits H2-incompatible FOR NO KEY UPDATE. */
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM mcp_server_entry WHERE id = :id FOR UPDATE", nativeQuery = true)
    java.util.Optional<McpServerEntry> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
