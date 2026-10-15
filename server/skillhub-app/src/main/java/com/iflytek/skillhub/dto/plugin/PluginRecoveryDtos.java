package com.iflytek.skillhub.dto.plugin;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class PluginRecoveryDtos {
    private PluginRecoveryDtos() {}

    public record ReplanRequest(
            @NotBlank String channel,
            @NotNull Long targetRevisionId,
            int expectedRevision,
            @NotBlank @Size(max = 10000) String reason,
            String previewDigest) {}

    public record GovernanceRequest(
            @NotBlank String channel,
            @NotBlank String action,
            int expectedRevision,
            @NotBlank @Size(max = 10000) String reason,
            String previewDigest) {}

    @Schema(requiredProperties = {"channel", "action", "expectedRevision", "effect", "digest"})
    public record GovernancePreview(
            String channel, String action, int expectedRevision, String effect, String digest) {}

    @Schema(
            requiredProperties = {
                "id",
                "action",
                "state",
                "planRevision",
                "snapshot",
                "requestedBy",
                "reason",
                "createdAt"
            })
    public record GovernanceOperationView(
            Long id,
            String action,
            String state,
            int planRevision,
            String snapshot,
            String sourceReceipt,
            String requestedBy,
            String reason,
            Instant createdAt,
            String receipt,
            String lastError) {}

    @Schema(requiredProperties = {"channel", "expectedRevision", "snapshot", "digest"})
    public record ReplanPreview(
            String channel, int expectedRevision, String snapshot, String digest) {}

    @Schema(requiredProperties = {"revision", "snapshot", "reason", "createdAt"})
    public record PlanRevisionView(
            int revision, String snapshot, String requestedBy, String reason, Instant createdAt) {}

    @Schema(
            requiredProperties = {
                "channel",
                "state",
                "originalSnapshot",
                "effectiveSnapshot",
                "planRevision",
                "sourceDisabled",
                "plans"
            })
    public record RecoveryChannelView(
            Long id,
            String channel,
            String state,
            String originalSnapshot,
            String effectiveSnapshot,
            int planRevision,
            boolean sourceDisabled,
            String receipt,
            String lastError,
            Instant leaseUntil,
            List<PlanRevisionView> plans,
            List<AttemptView> attempts,
            List<GovernanceOperationView> operations) {}

    @Schema(requiredProperties = {"items", "page", "hasMore"})
    public record AttemptPage(List<AttemptView> items, int page, boolean hasMore) {}

    @Schema(
            requiredProperties = {
                "id",
                "planRevision",
                "action",
                "outcome",
                "startedAt",
                "heartbeatAt",
                "leaseUntil"
            })
    public record AttemptView(
            Long id,
            int planRevision,
            String action,
            String outcome,
            Instant startedAt,
            Instant heartbeatAt,
            Instant leaseUntil,
            Instant finishedAt,
            String receipt,
            String errorCode,
            String lateReceipt,
            Long operationId) {}
}
