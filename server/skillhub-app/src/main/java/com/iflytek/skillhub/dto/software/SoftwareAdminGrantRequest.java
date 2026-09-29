package com.iflytek.skillhub.dto.software;

import jakarta.validation.constraints.NotBlank;

public record SoftwareAdminGrantRequest(
        @NotBlank String userId
) {
}
