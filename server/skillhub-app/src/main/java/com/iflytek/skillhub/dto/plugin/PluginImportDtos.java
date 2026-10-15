package com.iflytek.skillhub.dto.plugin;

import com.iflytek.skillhub.domain.plugin.PluginVisibility;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class PluginImportDtos {
    private PluginImportDtos() {}

    @Schema(requiredProperties = {"id", "directory", "runtime", "status"})
    public record ImportCandidateView(
            String id,
            String directory,
            String runtime,
            String nativeName,
            String version,
            String suggestedSlug,
            String sha256,
            String format,
            String inventoryJson,
            String manifestJson,
            String status,
            String error) {}

    @Schema(requiredProperties = {"id", "namespaceId", "repository", "expiresAt", "candidates"})
    public record ImportPreviewView(
            String id,
            Long namespaceId,
            boolean repository,
            Instant expiresAt,
            List<ImportCandidateView> candidates) {}

    public record ImportSelection(
            @NotBlank String candidateId,
            Long projectId,
            @Size(max = 64) String slug,
            PluginVisibility visibility) {}

    public record ImportSelectionRequest(
            @NotEmpty @Size(max = 1000) List<@Valid ImportSelection> selections) {}

    @Schema(requiredProperties = {"candidateId", "status"})
    public record ImportResultView(
            String candidateId, String status, Long projectId, Long releaseId, String error) {}

    @Schema(requiredProperties = {"results"})
    public record ImportResultsView(List<ImportResultView> results) {}
}
