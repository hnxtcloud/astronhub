package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/** Admin summary row of a software application with its lifecycle status. */
public record SoftwareAppAdminSummaryResponse(
        Long id,
        String slug,
        String name,
        String description,
        String iconObjectKey,
        String categoryCode,
        String categoryName,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
