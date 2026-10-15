package com.iflytek.skillhub.dto.software;
import java.time.Instant;
import java.util.List;

public record SoftwareUpdateReleaseResponse(Long id, Long applicationId, Long catalogReleaseId,
        String machineVersion, String tag, String targetOs, String targetArch, String channel, String variant,
        String protocol, String piVersion, String status, String releaseNotes, Instant publishedAt,
        List<SoftwareReleaseAssetResponse> assets) { }
