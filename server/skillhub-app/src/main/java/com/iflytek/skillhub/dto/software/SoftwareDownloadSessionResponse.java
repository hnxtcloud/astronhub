package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/** Issued download session; the URL stays inside the site and re-checks auth. */
public record SoftwareDownloadSessionResponse(
        String sessionId,
        String downloadUrl,
        Instant expiresAt
) {
}
