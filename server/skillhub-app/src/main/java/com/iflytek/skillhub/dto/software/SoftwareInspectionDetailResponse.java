package com.iflytek.skillhub.dto.software;

import java.util.List;

/** Admin detail of one inspection task: summary plus every recorded attempt. */
public record SoftwareInspectionDetailResponse(
        SoftwareInspectionSummaryResponse task,
        List<SoftwareInspectionAttemptResponse> attempts
) {
}
