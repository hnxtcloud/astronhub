package com.iflytek.skillhub.dto.software;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Replaces the full recommendation set of a release atomically. */
public record SoftwareRecommendationSetRequest(
        @NotNull @Valid List<SoftwareRecommendationItemRequest> recommendations
) {
}
