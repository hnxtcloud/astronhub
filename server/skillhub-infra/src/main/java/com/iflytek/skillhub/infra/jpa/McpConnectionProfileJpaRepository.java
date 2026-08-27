package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.mcp.McpConnectionProfile;
import com.iflytek.skillhub.domain.mcp.McpConnectionProfileRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface McpConnectionProfileJpaRepository
        extends JpaRepository<McpConnectionProfile, Long>, McpConnectionProfileRepository {
}
