package com.iflytek.skillhub.domain.mcp;

import java.util.List;

/**
 * Structured connection profile input for publication. The workflow validates this shape and
 * serializes canonical arguments/variables JSON onto {@link McpConnectionProfile} rows.
 */
public record McpProfileTemplate(
        String profileKey,
        String displayName,
        McpTransportType transportType,
        String endpointTemplate,
        String commandTemplate,
        List<String> arguments,
        List<McpVariableTemplate> variables) {}
