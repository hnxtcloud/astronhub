package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.mcp.McpEntryRevision;
import com.iflytek.skillhub.domain.mcp.McpEntryRevisionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface McpEntryRevisionJpaRepository
        extends JpaRepository<McpEntryRevision, Long>, McpEntryRevisionRepository {
    /** Native row locking preserves PostgreSQL/H2 semantics; Hibernate emits H2-incompatible FOR NO KEY UPDATE. */
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM mcp_entry_revision WHERE id = :id FOR UPDATE", nativeQuery = true)
    java.util.Optional<McpEntryRevision> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
