package com.iflytek.skillhub.dto.mcp;

import com.iflytek.skillhub.domain.mcp.McpEntryStatus;
import com.iflytek.skillhub.domain.mcp.McpTransportType;
import com.iflytek.skillhub.domain.mcp.McpVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

@Schema(description = "MCP catalog management: publication, review, and governance")
public final class McpManagementDtos {
    private McpManagementDtos() {}

    public record McpVariableInput(
            @Schema(description = "Variable name used in templates as ${name}") @NotBlank @Size(max = 64) String name,
            @Size(max = 1000) String description,
            Boolean required,
            @Schema(description = "Secret variables render as placeholders; values are never stored") Boolean secret,
            @Schema(description = "Optional non-secret default; rejected for secret variables") @Size(max = 1000) String defaultValue,
            @Size(max = 1000) String validationHint) {}

    public record McpProfileInput(
            @NotBlank @Size(max = 100) String profileKey,
            @NotBlank @Size(max = 200) String displayName,
            @NotNull McpTransportType transportType,
            @Size(max = 2000) String endpointTemplate,
            @Size(max = 2000) String commandTemplate,
            List<@Size(max = 1000) String> arguments,
            @Valid List<McpVariableInput> variables) {}

    public record McpEntryCreateRequest(
            @NotNull Long namespaceId,
            @NotBlank @Size(max = 100) String slug,
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 30000) String summary,
            @NotNull McpVisibility visibility,
            @Valid List<McpProfileInput> profiles,
            @Schema(description = "Optional raw server.json content; remotes and packages prefill connection templates")
            @Size(max = 200000) String serverJson) {}

    public record McpEntryUpdateRequest(
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 30000) String summary) {}

    public record McpRevisionCreateRequest(
            @Size(max = 30000) String description,
            @Valid List<McpProfileInput> profiles,
            @Size(max = 200000) String serverJson,
            @Size(max = 30000) String localCuration) {}

    public record McpRevisionUpdateRequest(
            @Size(max = 30000) String description,
            @Valid List<McpProfileInput> profiles,
            @Size(max = 30000) String localCuration) {}

    public record McpReviewRequest(@Size(max = 10000) String notes) {}

    public record McpRejectRequest(@NotBlank @Size(max = 10000) String notes) {}

    public record McpGovernanceRequest(
            @NotBlank String action,
            McpVisibility visibility,
            @NotBlank @Size(max = 10000) String reason) {}

    public record McpVariableView(
            String name, String description, boolean required, boolean secret,
            String defaultValue, String validationHint) {}

    public record McpProfileView(
            String profileKey, String displayName, McpTransportType transportType,
            String endpointTemplate, String commandTemplate,
            List<String> arguments, List<McpVariableView> variables) {}

    public record McpReviewDecisionView(String reviewerId, String decision, String notes, Instant createdAt) {}

    public record McpRevisionView(
            Long id, int revisionNumber, String status, String description,
            String sourceKind, String localCuration, Instant createdAt, Instant publishedAt,
            List<McpProfileView> profiles, List<McpReviewDecisionView> reviews) {}

    public record McpEntryView(
            Long id, Long namespaceId, String namespace, String slug, String displayName, String summary,
            String ownerId, McpVisibility visibility, McpEntryStatus status, boolean hidden,
            Long latestRevisionId, boolean canManage, boolean canReview, boolean canGovern,
            boolean pendingReview, List<McpRevisionView> revisions) {}
}
