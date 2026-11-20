package com.iflytek.skillhub.domain.mcp;

import java.util.List;

public interface McpReviewDecisionRepository {
    List<McpReviewDecision> findByRevisionIdOrderByCreatedAtDesc(Long revisionId);
    McpReviewDecision save(McpReviewDecision decision);
}
