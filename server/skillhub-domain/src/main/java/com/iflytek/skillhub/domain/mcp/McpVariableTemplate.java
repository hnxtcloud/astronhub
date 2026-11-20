package com.iflytek.skillhub.domain.mcp;

/**
 * Structured variable definition inside a connection profile template. Secret values are never
 * carried here — only placeholders and metadata (docs/25 "Configuration Variables").
 */
public record McpVariableTemplate(
        String name,
        String description,
        boolean required,
        boolean secret,
        String defaultValue,
        String validationHint) {}
