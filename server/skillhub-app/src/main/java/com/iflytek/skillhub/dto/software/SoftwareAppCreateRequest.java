package com.iflytek.skillhub.dto.software;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SoftwareAppCreateRequest(
        @NotBlank @Size(max = 100) String slug,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 20000) String description,
        @Size(max = 512) String iconObjectKey,
        @Size(max = 64) String categoryCode
) {
}
