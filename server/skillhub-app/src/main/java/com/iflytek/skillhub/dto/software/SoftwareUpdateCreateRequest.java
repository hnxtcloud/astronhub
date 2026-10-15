package com.iflytek.skillhub.dto.software;

public record SoftwareUpdateCreateRequest(@jakarta.validation.constraints.NotNull Long catalogReleaseId, String machineVersion, String tag,
        String targetOs, String targetArch, String channel, String variant, String protocol, String piVersion) { }
