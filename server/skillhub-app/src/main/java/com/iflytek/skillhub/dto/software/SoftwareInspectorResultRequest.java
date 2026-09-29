package com.iflytek.skillhub.dto.software;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Inspector result submission for one attempt. {@code outcome} is one of
 * SUCCEEDED, BASIC_FAILED (blocks publication), METADATA_FAILED (manual
 * fill-in allowed), INFRA_FAILED (recoverable; consumes one auto-retry).
 */
public record SoftwareInspectorResultRequest(
        @NotBlank String workerId,
        @NotBlank String attemptToken,
        @NotBlank String outcome,
        String errorCode,
        JsonNode rawVersionFields,
        String parserName,
        String parserVersion,
        Instant startedAt,
        Instant finishedAt
) {
}
