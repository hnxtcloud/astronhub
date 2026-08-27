package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.mcp.McpEntryRevision;
import com.iflytek.skillhub.domain.mcp.McpEntryRevisionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface McpEntryRevisionJpaRepository
        extends JpaRepository<McpEntryRevision, Long>, McpEntryRevisionRepository {
}
