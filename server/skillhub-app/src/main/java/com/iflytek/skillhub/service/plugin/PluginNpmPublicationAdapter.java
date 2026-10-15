package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.PluginPublicationProperties;
import com.iflytek.skillhub.domain.plugin.PluginDistribution;
import com.iflytek.skillhub.domain.plugin.PluginNpmRegistry;
import com.iflytek.skillhub.domain.plugin.PluginNpmRegistryRepository;
import com.iflytek.skillhub.domain.plugin.PluginNpmSelectionRepository;
import com.iflytek.skillhub.domain.plugin.PluginProject;
import com.iflytek.skillhub.domain.plugin.PluginRelease;
import com.iflytek.skillhub.domain.plugin.PluginVisibility;
import com.iflytek.skillhub.storage.ObjectStorageService;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class PluginNpmPublicationAdapter implements PluginPublicationChannelAdapter {
    private final PluginNpmRegistryRepository registries;
    private final PluginNpmSelectionRepository selections;
    private final PluginPublicationProperties properties;
    private final ObjectMapper mapper;
    private final NpmRegistryPublisher publisher;
    private final ObjectStorageService storage;

    public PluginNpmPublicationAdapter(
            PluginNpmRegistryRepository registries,
            PluginNpmSelectionRepository selections,
            PluginPublicationProperties properties,
            ObjectMapper mapper,
            NpmRegistryPublisher publisher,
            ObjectStorageService storage) {
        this.registries = registries;
        this.selections = selections;
        this.properties = properties;
        this.mapper = mapper;
        this.publisher = publisher;
        this.storage = storage;
    }

    public String channel() {
        return "NPM";
    }

    public record Snapshot(
            Long distributionId,
            String parserVersion,
            NpmRegistryPublisher.Target target,
            String key,
            String sha256,
            String manifestJson,
            String inventoryJson) {}

    public String snapshot(
            PluginProject project, PluginRelease release, List<PluginDistribution> distributions) {
        if (project.getVisibility() != PluginVisibility.PUBLIC) return null;
        var registry =
                selections
                        .findById(release.getId())
                        .flatMap(s -> registries.findById(s.getRegistryId()))
                        .orElseGet(
                                () ->
                                        registries.findAllByOrderByIdDesc().stream()
                                                .filter(PluginNpmRegistry::isDefaultRegistry)
                                                .findFirst()
                                                .orElse(null));
        var pi =
                distributions.stream()
                        .filter(d -> d.getRuntimeKey().equals("pi"))
                        .findFirst()
                        .orElseThrow();
        if (registry == null || !properties.available(registry.getCredentialRef())) return null;
        try {
            if (!mapper.readTree(pi.getInventoryJson())
                    .path("packaging")
                    .asText()
                    .equals("ORIGINAL_NPM_TGZ")) return null;
            return mapper.writeValueAsString(
                    new Snapshot(
                            pi.getId(),
                            "pi-package-json",
                            new NpmRegistryPublisher.Target(
                                    registry.getId(),
                                    registry.getRegistry(),
                                    registry.getCredentialRef()),
                            pi.getSourceLocator(),
                            pi.getIntegrityDigest(),
                            pi.getManifestJson(),
                            pi.getInventoryJson()));
        } catch (Exception ex) {
            return null;
        }
    }

    public String publish(PluginChannelContext context) throws Exception {
        return execute(context, false).orElseThrow();
    }

    @Override
    public Inspection reconcile(PluginChannelContext context) throws Exception {
        var result = execute(context, true);
        return result.isPresent()
                ? new Inspection("PRESENT", result.get(), null)
                : new Inspection("ABSENT", null, "REMOTE_CONTENT_ABSENT");
    }

    private java.util.Optional<String> execute(PluginChannelContext context, boolean inspect)
            throws Exception {
        Snapshot plan = mapper.readValue(context.snapshot(), Snapshot.class);
        Path archive = Files.createTempFile("astronhub-npm-", ".tgz");
        try {
            try (var input = storage.getObject(plan.key());
                    var output = Files.newOutputStream(archive)) {
                copyBounded(input, output);
            }
            if (!OfflinePluginScanner.sha256(archive).equals(plan.sha256()))
                throw new NpmRegistryPublisher.Failure("PACKAGE_DIGEST_MISMATCH");
            if (inspect)
                return publisher
                        .reconcile(
                                archive,
                                plan.manifestJson(),
                                plan.target(),
                                properties.resolve(plan.target().credentialRef()))
                        .map(
                                value -> {
                                    try {
                                        return mapper.writeValueAsString(value);
                                    } catch (IOException ex) {
                                        throw new IllegalStateException(ex);
                                    }
                                });
            return java.util.Optional.of(
                    mapper.writeValueAsString(
                            publisher.publish(
                                    archive,
                                    plan.manifestJson(),
                                    plan.target(),
                                    properties.resolve(plan.target().credentialRef()))));
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    public static long copyBounded(InputStream input, OutputStream output) throws IOException {
        long size = 0;
        byte[] bytes = new byte[8192];
        int n;
        while ((n = input.read(bytes)) != -1) {
            size += n;
            if (size > NpmRegistryPublisher.MAX_BYTES)
                throw new NpmRegistryPublisher.Failure("PACKAGE_SIZE_LIMIT");
            output.write(bytes, 0, n);
        }
        return size;
    }
}
