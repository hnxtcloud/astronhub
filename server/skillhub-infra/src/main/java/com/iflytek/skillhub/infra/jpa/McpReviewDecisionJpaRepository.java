package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.mcp.McpReviewDecision;
import com.iflytek.skillhub.domain.mcp.McpReviewDecisionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface McpReviewDecisionJpaRepository
        extends JpaRepository<McpReviewDecision, Long>, McpReviewDecisionRepository {
}
