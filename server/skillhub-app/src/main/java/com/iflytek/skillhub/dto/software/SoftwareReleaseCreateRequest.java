package com.iflytek.skillhub.dto.software;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SoftwareReleaseCreateRequest(
        @NotBlank @Size(max = 255) String displayVersion,
        @Size(max = 20000) String releaseNotes
) {
}
