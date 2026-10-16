package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.domain.software.SoftwarePublisherGrant;
import com.iflytek.skillhub.domain.software.SoftwarePublisherGrantRepository;
import com.iflytek.skillhub.dto.software.SoftwarePublisherResponse;

import org.springframework.stereotype.Repository;

import java.util.List;

/** Joins application grants and current token facts for the secret-free publisher view. */
@Repository
public class SoftwarePublisherQueryRepository {
    private final SoftwarePublisherGrantRepository grants;
    private final ApiTokenRepository tokens;

    public SoftwarePublisherQueryRepository(
            SoftwarePublisherGrantRepository grants, ApiTokenRepository tokens) {
        this.grants = grants;
        this.tokens = tokens;
    }

    public List<SoftwarePublisherResponse> list(Long appId) {
        return grants.findByApplicationIdOrderByCreatedAtDesc(appId).stream()
                .map(grant -> project(grant, tokens.findById(grant.getTokenId()).orElseThrow()))
                .toList();
    }

    public SoftwarePublisherResponse project(SoftwarePublisherGrant grant, ApiToken token) {
        return new SoftwarePublisherResponse(
                grant.getTokenId(),
                grant.getApplicationId(),
                grant.getOwnerUserId(),
                token.getName(),
                token.getExpiresAt(),
                grant.getRevokedAt() != null ? grant.getRevokedAt() : token.getRevokedAt(),
                grant.getCreatedAt());
    }
}
