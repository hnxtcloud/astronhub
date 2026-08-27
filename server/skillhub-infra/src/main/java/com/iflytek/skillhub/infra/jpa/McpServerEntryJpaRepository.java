package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.mcp.McpServerEntry;
import com.iflytek.skillhub.domain.mcp.McpServerEntryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface McpServerEntryJpaRepository
        extends JpaRepository<McpServerEntry, Long>, McpServerEntryRepository {
}
