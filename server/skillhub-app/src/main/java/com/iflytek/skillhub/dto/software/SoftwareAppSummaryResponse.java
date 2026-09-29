package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/** Portal summary row of a published software application. */
public record SoftwareAppSummaryResponse(
        Long id,
        String slug,
        String name,
        String description,
        String iconObjectKey,
        String categoryCode,
        String categoryName,
        Instant createdAt
) {
}
