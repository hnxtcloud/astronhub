package com.iflytek.skillhub.dto.plugin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PluginGitDtos {
    private PluginGitDtos() {}

    public record GitTargetRequest(
            @NotNull Long namespaceId,
            @NotBlank @Size(max = 2000) String remote,
            @NotBlank @Size(max = 2000) String fetchUrl,
            @NotBlank @Size(max = 200) String branch,
            @NotBlank @Size(max = 120) String credentialRef) {}

    public record GitTargetView(
            Long id,
            Long namespaceId,
            String namespace,
            String remote,
            String fetchUrl,
            String branch,
            String credentialRef,
            boolean credentialAvailable,
            boolean selected,
            Instant createdAt) {}

    public record GitNamespaceView(Long id, String slug, String displayName) {}

    public record GitSettingsView(
            List<String> allowedRemotes,
            List<String> credentialRefs,
            List<GitNamespaceView> namespaces,
            List<GitTargetView> targets) {}

    public record GitIntentView(
            Long id,
            String parent,
            String tree,
            String commit,
            String planDigest,
            Instant createdAt) {}
}
