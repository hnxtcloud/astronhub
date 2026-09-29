package com.iflytek.skillhub.dto.software;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Rollback request: creates a new revision from a historical one's values. */
public record SoftwareSettingsRollbackRequest(
        @NotNull Long fromRevision,
        @NotBlank @jakarta.validation.constraints.Size(max = 512) String changeReason
) {
}
