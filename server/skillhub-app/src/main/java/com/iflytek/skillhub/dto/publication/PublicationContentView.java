package com.iflytek.skillhub.dto.publication;

import java.time.Instant;

/** Content identity and container state; version/review state is read on the owning detail page. */
public record PublicationContentView(String type, Long resourceId, String namespace, String slug,
        String name, String visibility, String status, String version, Instant updatedAt) {}
