package com.iflytek.skillhub.dto.catalog;

import java.time.Instant;
import java.util.List;

public record CapabilityCatalogItem(
        CapabilityType type,
        Long id,
        String coordinate,
        String namespace,
        String slug,
        String displayName,
        String summary,
        String visibility,
        String status,
        String version,
        List<String> targets,
        long primaryMetric,
        Instant updatedAt
) {
}
