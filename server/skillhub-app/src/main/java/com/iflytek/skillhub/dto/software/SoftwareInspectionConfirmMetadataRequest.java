package com.iflytek.skillhub.dto.software;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/**
 * Administrator metadata fill-in for a package whose inspection could not
 * extract version fields. Rejected when the revision already carries
 * extracted fields, and rejected entirely while basic validation failed.
 */
public record SoftwareInspectionConfirmMetadataRequest(
        @NotNull JsonNode rawVersionFields
) {
}
