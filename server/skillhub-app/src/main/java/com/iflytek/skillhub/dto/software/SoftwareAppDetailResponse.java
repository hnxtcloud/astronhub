package com.iflytek.skillhub.dto.software;

import java.time.Instant;
import java.util.List;

/** Full application detail: profile plus releases, packages, recommendations. */
public record SoftwareAppDetailResponse(
        Long id,
        String slug,
        String name,
        String description,
        String iconObjectKey,
        String categoryCode,
        String categoryName,
        String status,
        Instant createdAt,
        List<SoftwareReleaseResponse> releases
) {
}
