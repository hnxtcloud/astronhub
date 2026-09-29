package com.iflytek.skillhub.dto.software;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** One recorded inspection attempt as shown in the admin inspection detail. */
public record SoftwareInspectionAttemptResponse(
        Integer attemptNo,
        String trigger,
        String outcome,
        String errorCode,
        JsonNode rawVersionFields,
        String parserName,
        String parserVersion,
        Instant startedAt,
        Instant finishedAt
) {
}
