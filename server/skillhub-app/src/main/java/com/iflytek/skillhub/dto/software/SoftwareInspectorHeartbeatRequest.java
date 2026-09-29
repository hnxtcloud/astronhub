package com.iflytek.skillhub.dto.software;

import jakarta.validation.constraints.NotBlank;

/** Inspector heartbeat payload for a running attempt. */
public record SoftwareInspectorHeartbeatRequest(
        @NotBlank String workerId,
        @NotBlank String attemptToken
) {
}
