package com.iflytek.skillhub.dto.software;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Settings update carrying the expected active revision for optimistic
 * concurrency plus a mandatory human-readable change reason.
 */
public record SoftwareSettingsUpdateRequest(
        @NotNull Long expectedRevision,
        @NotBlank @jakarta.validation.constraints.Size(max = 512) String changeReason,
        @NotNull @Valid SoftwarePolicyConfigDto config
) {
}
