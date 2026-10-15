package com.iflytek.skillhub.dto.software;
public record SoftwareUpdateChannelRequest(String slug, String virtualProjectPath, String targetOs,
        String targetArch, String channel, String variant, Long targetReleaseId) { }
