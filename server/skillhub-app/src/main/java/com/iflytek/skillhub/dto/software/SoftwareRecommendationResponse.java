package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/** A download recommendation bound to a package revision and target scope. */
public record SoftwareRecommendationResponse(
        Long id,
        Long packageRevisionId,
        String targetOs,
        String targetArch,
        String targetDistro,
        String format,
        String note,
        Instant createdAt
) {
}
