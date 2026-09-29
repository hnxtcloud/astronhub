package com.iflytek.skillhub.dto.software;

import java.time.Instant;

/** A package revision as shown to portal users and administrators. */
public record SoftwarePackageResponse(
        Long id,
        int revisionNo,
        String status,
        String targetOs,
        String targetArch,
        String targetDistro,
        String format,
        String originalFullVersion,
        String fileName,
        long byteSize,
        String sha256,
        String applicabilityNote,
        String confirmedBy,
        Instant confirmedAt,
        Instant publishedAt
) {
}
