package com.iflytek.skillhub.dto.software;

import java.time.Instant;
public record SoftwarePublisherResponse(Long tokenId, Long applicationId, String ownerUserId, String name,
        Instant expiresAt, Instant revokedAt, Instant createdAt) { }
