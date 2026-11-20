package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.mcp.McpConnectionProfile;
import com.iflytek.skillhub.domain.mcp.McpConnectionProfileRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface McpConnectionProfileJpaRepository
        extends JpaRepository<McpConnectionProfile, Long>, McpConnectionProfileRepository {
    /**
     * Bulk delete executes immediately so a same-key rebuild inside one flush cannot collide with
     * the UNIQUE(revision_id, profile_key) constraint.
     */
    @Override
    @Modifying
    @Query("delete from McpConnectionProfile p where p.revisionId = :revisionId")
    void deleteByRevisionId(@Param("revisionId") Long revisionId);
}
