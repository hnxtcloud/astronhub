package com.iflytek.skillhub.dto.software;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Native bare manifest contract; additional original metadata fields may be preserved. */
@Schema(requiredProperties = {"schemaVersion", "version", "piVersion", "platform", "arch", "channel",
        "publishedAt", "package", "sha256", "bytes"})
public record SoftwareBaizeUpdateManifest(int schemaVersion, String version, String piVersion,
        String platform, String arch, String channel,
        @Schema(format = "date-time") String publishedAt,
        @JsonProperty("package") String packageUrl, String sha256, long bytes, String releasesUrl) { }
