package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.PluginPublicationProperties;
import com.iflytek.skillhub.domain.plugin.PluginNpmDependencyUpload;
import com.iflytek.skillhub.domain.plugin.PluginNpmDependencyUploadRepository;
import com.iflytek.skillhub.domain.plugin.PluginNpmRegistry;
import com.iflytek.skillhub.domain.plugin.PluginNpmRegistryRepository;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.DependencyUploadView;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.RegistryView;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.SettingsView;
import com.iflytek.skillhub.service.plugin.NpmRegistryPublisher;

import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Secret-free settings/dependency projections; immutable receipts may contain only public
 * locations.
 */
@Repository
public class PluginNpmQueryRepository {
    private final PluginNpmRegistryRepository registries;
    private final PluginNpmDependencyUploadRepository uploads;
    private final PluginPublicationProperties properties;
    private final ObjectMapper mapper;

    public PluginNpmQueryRepository(
            PluginNpmRegistryRepository registries,
            PluginNpmDependencyUploadRepository uploads,
            PluginPublicationProperties properties,
            ObjectMapper mapper) {
        this.registries = registries;
        this.uploads = uploads;
        this.properties = properties;
        this.mapper = mapper;
    }

    public RegistryView registry(PluginNpmRegistry value) {
        return new RegistryView(
                value.getId(),
                value.getName(),
                value.getRegistry(),
                value.getCredentialRef(),
                properties.available(value.getCredentialRef()),
                value.isDefaultRegistry());
    }

    public SettingsView settings() {
        return new SettingsView(
                List.copyOf(properties.getAllowedRegistries()),
                properties.getCredentials().keySet().stream().sorted().toList(),
                registries.findAllByOrderByIdDesc().stream().map(this::registry).toList());
    }

    public DependencyUploadView upload(PluginNpmDependencyUpload value) {
        try {
            return new DependencyUploadView(
                    value.getId(),
                    value.getUploadedBy(),
                    value.getSource(),
                    value.getName(),
                    value.getVersion(),
                    value.getSha256(),
                    mapper.readValue(value.getSnapshot(), NpmRegistryPublisher.Target.class)
                            .registry(),
                    value.getState(),
                    value.getAttemptCount(),
                    value.getLastError(),
                    value.getReceipt(),
                    value.getManifestJson());
        } catch (Exception ex) {
            throw new IllegalStateException("INVALID_DEPENDENCY_SNAPSHOT");
        }
    }

    public List<DependencyUploadView> uploads() {
        return uploads.findTop100ByOrderByIdDesc().stream().map(this::upload).toList();
    }
}
