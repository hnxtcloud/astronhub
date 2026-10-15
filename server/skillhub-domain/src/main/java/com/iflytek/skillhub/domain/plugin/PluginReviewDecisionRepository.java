package com.iflytek.skillhub.domain.plugin;
import java.util.List;
public interface PluginReviewDecisionRepository {
    PluginReviewDecision save(PluginReviewDecision decision);
    List<PluginReviewDecision> findByReleaseIdOrderByCreatedAtDesc(Long releaseId);
}
