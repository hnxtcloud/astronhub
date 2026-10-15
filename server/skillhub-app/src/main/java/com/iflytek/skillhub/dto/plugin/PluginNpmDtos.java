package com.iflytek.skillhub.dto.plugin;

import com.iflytek.skillhub.service.plugin.NpmRegistryPublisher;

import jakarta.validation.constraints.*;

import java.util.List;

public final class PluginNpmDtos {
    private PluginNpmDtos() {}

    public record RegistryRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 2000) String registry,
            @NotBlank @Size(max = 120) String credentialRef,
            boolean defaultRegistry) {}

    public record RegistryView(
            Long id,
            String name,
            String registry,
            String credentialRef,
            boolean credentialAvailable,
            boolean defaultRegistry) {}

    public record SettingsView(
            List<String> allowedRegistries,
            List<String> credentialRefs,
            List<RegistryView> registries) {}

    public record RegistrySelection(@NotNull Long registryId) {}

    public record DependencyUploadView(
            Long id,
            String uploadedBy,
            String source,
            String name,
            String version,
            String sha256,
            String registry,
            String state,
            int attemptCount,
            String lastError,
            String receipt,
            String requirementsJson) {}

    public record ChannelView(
            String channel,
            String state,
            String snapshot,
            int attemptCount,
            String lastError,
            String receipt) {}

    public record PrerequisitesView(
            String registry,
            List<NpmRegistryPublisher.Dependency> dependencies,
            String requirementsJson,
            String installCommand,
            String registryConfiguration,
            String runtimeTest) {}
}
