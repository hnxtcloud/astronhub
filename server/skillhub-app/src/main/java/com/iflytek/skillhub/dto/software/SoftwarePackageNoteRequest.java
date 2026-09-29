package com.iflytek.skillhub.dto.software;

import jakarta.validation.constraints.Size;

public record SoftwarePackageNoteRequest(
        @Size(max = 512) String applicabilityNote
) {
}
