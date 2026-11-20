package com.iflytek.skillhub.dto.catalog;

import com.iflytek.skillhub.domain.mcp.McpTransportType;
import java.time.Instant;
import java.util.List;

/** Published connection templates; variable definitions never include credential values. */
public record McpConnectionProfilesResponse(
        int revision,
        String description,
        Instant publishedAt,
        List<McpConnectionProfileResponse> profiles) {
    public record McpConnectionProfileResponse(
            String profileKey,
            String displayName,
            McpTransportType transportType,
            String endpointTemplate,
            String commandTemplate,
            List<String> arguments,
            List<McpVariableResponse> variables,
            boolean configurationAvailable) {
    }

    public record McpVariableResponse(
            String name,
            String description,
            boolean required,
            boolean secret,
            String defaultValue,
            String validationHint) {
    }
}
