package com.iflytek.skillhub.service.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.mcp.McpProfileTemplate;
import com.iflytek.skillhub.domain.mcp.McpTransportType;
import com.iflytek.skillhub.domain.mcp.McpVariableTemplate;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Translates pinned standard server.json content into MCP profile templates (docs/25 catalog
 * source "imported standard server.json metadata"). This adapter stays outside the MCP domain;
 * the raw document is stored verbatim as the revision source snapshot. Header and literal secret
 * values are never carried into templates.
 */
@Component
public class ServerJsonImportParser {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private final ObjectMapper mapper;

    public ServerJsonImportParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record ServerJsonImport(
            String displayName, String summary, List<McpProfileTemplate> profiles, String snapshotJson) {}

    public ServerJsonImport parse(String serverJson) {
        JsonNode root;
        try {
            root = mapper.readTree(serverJson);
        } catch (JsonProcessingException exception) {
            throw new DomainBadRequestException("error.mcp.importInvalid");
        }
        if (root == null || !root.isObject()) throw new DomainBadRequestException("error.mcp.importInvalid");
        String name = root.path("name").asText("");
        if (name.isBlank()) throw new DomainBadRequestException("error.mcp.importInvalid");
        String title = textOrNull(root, "title");
        String displayName = title == null || title.isBlank() ? humanize(lastSegment(name)) : title;
        String summary = textOrNull(root, "description");

        List<McpProfileTemplate> profiles = new ArrayList<>();
        int remoteIndex = 0;
        for (JsonNode remote : root.path("remotes")) {
            remoteIndex++;
            String url = textOrNull(remote, "url");
            if (url == null) continue;
            String type = remote.path("type").asText("").toLowerCase(java.util.Locale.ROOT);
            McpTransportType transport = switch (type) {
                case "streamable-http", "streamable_http", "http" -> McpTransportType.STREAMABLE_HTTP;
                case "sse" -> McpTransportType.LEGACY_HTTP_SSE;
                default -> McpTransportType.CUSTOM;
            };
            profiles.add(new McpProfileTemplate(
                    "remote-" + remoteIndex,
                    humanize(transport.name().toLowerCase(java.util.Locale.ROOT)) + " (" + remoteIndex + ")",
                    transport, url, null,
                    List.of(), urlVariables(url)));
        }
        int packageIndex = 0;
        for (JsonNode pkg : root.path("packages")) {
            packageIndex++;
            String identifier = textOrNull(pkg, "identifier");
            if (identifier == null) continue;
            String registryType = firstText(pkg, "registry_type", "registryType").toLowerCase(java.util.Locale.ROOT);
            String version = textOrNull(pkg, "version");
            String command;
            List<String> arguments = new ArrayList<>();
            switch (registryType) {
                case "npm" -> {
                    command = "npx";
                    arguments.add("-y");
                    arguments.add(version == null ? identifier : identifier + "@" + version);
                }
                case "pypi" -> {
                    command = "uvx";
                    arguments.add(version == null ? identifier : identifier + "@" + version);
                }
                default -> {
                    continue;
                }
            }
            for (JsonNode argument : pkg.path("package_arguments")) {
                String value = firstText(argument, "value", "valueHint");
                if (value == null) continue;
                if ("named".equals(argument.path("type").asText())) {
                    String argumentName = textOrNull(argument, "name");
                    if (argumentName != null) arguments.add("--" + argumentName);
                }
                arguments.add(value);
            }
            profiles.add(new McpProfileTemplate(
                    "package-" + packageIndex,
                    humanize(lastSegment(identifier)) + " (" + packageIndex + ")",
                    McpTransportType.STDIO, null, command, arguments,
                    environmentVariables(pkg.path("environment_variables"), arguments)));
        }
        return new ServerJsonImport(displayName, summary, List.copyOf(profiles), serverJson);
    }

    /** URL placeholders become required variables; credential-looking names stay secret. */
    private List<McpVariableTemplate> urlVariables(String url) {
        List<McpVariableTemplate> variables = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(url);
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!seen.add(name)) continue;
            variables.add(new McpVariableTemplate(
                    name, null, true, isCredentialName(name), null, null));
        }
        return variables;
    }

    private List<McpVariableTemplate> environmentVariables(JsonNode env, List<String> arguments) {
        List<McpVariableTemplate> variables = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode variable : env) {
            String name = textOrNull(variable, "name");
            if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*") || !seen.add(name)) continue;
            String value = textOrNull(variable, "value");
            boolean required = variable.path("required").asBoolean(false);
            if (value == null) {
                // No literal value in the source document: users supply it at connection time.
                variables.add(new McpVariableTemplate(
                        name, textOrNull(variable, "description"), required, true, null, null));
            } else if (isCredentialName(name)) {
                // Literal credential values from the source document are never persisted (docs/25).
                variables.add(new McpVariableTemplate(
                        name, textOrNull(variable, "description"), true, true, null,
                        "Value omitted from the imported server.json; supply it at connection time."));
            } else {
                Matcher matcher = PLACEHOLDER.matcher(value);
                while (matcher.find()) {
                    String placeholder = matcher.group(1);
                    if (seen.add(placeholder) && isCredentialName(placeholder)) {
                        variables.add(new McpVariableTemplate(placeholder, null, true, true, null, null));
                    }
                }
                variables.add(new McpVariableTemplate(
                        name, textOrNull(variable, "description"), required, false, value, null));
            }
        }
        arguments.stream()
                .flatMap(argument -> PLACEHOLDER.matcher(argument).results())
                .map(match -> match.group(1))
                .filter(seen::add)
                .forEach(name -> variables.add(
                        new McpVariableTemplate(name, null, true, isCredentialName(name), null, null)));
        return variables;
    }

    private boolean isCredentialName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("key") || lower.contains("token") || lower.contains("secret")
                || lower.contains("password") || lower.contains("credential");
    }

    private String firstText(JsonNode node, String primary, String fallback) {
        String value = textOrNull(node, primary);
        return value != null ? value : textOrNull(node, fallback);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private String lastSegment(String name) {
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private String humanize(String value) {
        String[] words = value.split("[-_]");
        StringBuilder text = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!text.isEmpty()) text.append(' ');
            text.append(Character.toUpperCase(word.charAt(0)));
            text.append(word.substring(1));
        }
        return text.isEmpty() ? value : text.toString();
    }
}
