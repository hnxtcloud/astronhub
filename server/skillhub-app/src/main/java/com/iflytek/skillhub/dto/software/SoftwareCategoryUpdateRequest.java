package com.iflytek.skillhub.dto.software;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SoftwareCategoryUpdateRequest(
        @NotBlank @Size(max = 128) String name,
        int sortOrder
) {
}
