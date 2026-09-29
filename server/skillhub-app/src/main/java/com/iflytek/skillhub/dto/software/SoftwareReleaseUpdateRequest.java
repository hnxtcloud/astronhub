package com.iflytek.skillhub.dto.software;

import jakarta.validation.constraints.Size;

public record SoftwareReleaseUpdateRequest(
        @Size(max = 20000) String releaseNotes
) {
}
