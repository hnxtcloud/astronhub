package com.iflytek.skillhub.dto.plugin;

import com.iflytek.skillhub.domain.plugin.PluginVisibility;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.PluginDistributionView;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class PluginAccessDtos {
    private PluginAccessDtos() {}

    @Schema(
            requiredProperties = {
                "id",
                "namespace",
                "slug",
                "displayName",
                "visibility",
                "versions",
                "components"
            })
    public record PublishedPluginView(
            Long id,
            String namespace,
            String slug,
            String displayName,
            String summary,
            PluginVisibility visibility,
            Long latestReleaseId,
            List<PublishedPluginVersion> versions,
            List<PluginLinkedComponentView> components) {}

    @Schema(requiredProperties = {"id", "version", "publishedAt", "runtimes"})
    public record PublishedPluginVersion(
            Long id, String version, Instant publishedAt, List<String> runtimes) {}

    @Schema(requiredProperties = {"id", "projectId", "version", "publishedAt", "distributions"})
    public record PublishedPluginReleaseView(
            Long id,
            Long projectId,
            String version,
            String changelog,
            Instant publishedAt,
            List<PluginDistributionView> distributions) {}

    @Schema(requiredProperties = {"files"})
    public record PluginFileListView(List<String> files) {}

    @Schema(requiredProperties = {"path", "content", "bytes"})
    public record PluginFileContentView(String path, String content, long bytes) {}

    @Schema(
            requiredProperties = {
                "runtime",
                "nativeName",
                "version",
                "downloadPath",
                "filename",
                "sha256",
                "command",
                "preparation",
                "runtimeVerified"
            })
    public record PluginInstallView(
            String runtime,
            String nativeName,
            String version,
            String downloadPath,
            String filename,
            String sha256,
            String command,
            String preparation,
            String marketplaceJson,
            String powershellCommand,
            String registry,
            boolean runtimeVerified) {}

    public record PluginGovernanceRequest(
            @NotBlank String action,
            PluginVisibility visibility,
            @NotBlank @Size(max = 10000) String reason) {}

    public record PluginYankRequest(@NotBlank @Size(max = 10000) String reason) {}

    public record PluginComponentLinkRequest(
            @NotBlank String kind,
            @NotBlank String namespace,
            @NotBlank String slug,
            @NotBlank String version) {}

    @Schema(requiredProperties = {"id", "kind", "namespace", "slug", "version", "displayName"})
    public record PluginLinkedComponentView(
            Long id,
            String kind,
            String namespace,
            String slug,
            String version,
            String displayName) {}
}
