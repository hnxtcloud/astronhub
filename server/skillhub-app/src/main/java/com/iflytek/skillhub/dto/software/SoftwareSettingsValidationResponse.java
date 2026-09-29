package com.iflytek.skillhub.dto.software;

import java.util.List;

/** Result of a dry-run policy validation. */
public record SoftwareSettingsValidationResponse(
        boolean valid,
        List<FieldViolation> violations
) {
    public record FieldViolation(String field, String messageCode, List<Object> messageArgs) {
    }
}
