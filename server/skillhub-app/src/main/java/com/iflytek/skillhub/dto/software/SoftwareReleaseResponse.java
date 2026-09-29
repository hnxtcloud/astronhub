package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/** A release with its packages and active recommendations. */
public record SoftwareReleaseResponse(
        Long id,
        String displayVersion,
        String releaseNotes,
        Instant publishedAt,
        int packageCount,
        java.util.List<SoftwarePackageResponse> packages,
        java.util.List<SoftwareRecommendationResponse> recommendations
) {
}
