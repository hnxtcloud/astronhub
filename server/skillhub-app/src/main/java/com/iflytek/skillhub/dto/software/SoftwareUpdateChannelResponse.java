package com.iflytek.skillhub.dto.software;
public record SoftwareUpdateChannelResponse(Long id, String slug, String virtualProjectPath, String targetOs,
        String targetArch, String channel, String variant, Long targetReleaseId) { }
