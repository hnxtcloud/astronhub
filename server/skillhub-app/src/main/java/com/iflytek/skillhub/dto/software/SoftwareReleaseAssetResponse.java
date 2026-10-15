package com.iflytek.skillhub.dto.software;

/** No storage credentials or private object keys cross the management boundary. */
public record SoftwareReleaseAssetResponse(Long id, String relativePath, String role, Long packageRevisionId,
        long byteSize, String sha256, String sha512Base64) { }
