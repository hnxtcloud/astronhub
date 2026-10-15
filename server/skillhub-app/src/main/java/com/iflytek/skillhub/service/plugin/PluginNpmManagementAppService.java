package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.PluginPublicationProperties;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.plugin.PluginDistributionRepository;
import com.iflytek.skillhub.domain.plugin.PluginNpmDependencyUpload;
import com.iflytek.skillhub.domain.plugin.PluginNpmDependencyUploadRepository;
import com.iflytek.skillhub.domain.plugin.PluginNpmRegistry;
import com.iflytek.skillhub.domain.plugin.PluginNpmRegistryRepository;
import com.iflytek.skillhub.domain.plugin.PluginNpmSelection;
import com.iflytek.skillhub.domain.plugin.PluginNpmSelectionRepository;
import com.iflytek.skillhub.domain.plugin.PluginPublicationChannel;
import com.iflytek.skillhub.domain.plugin.PluginPublicationChannelRepository;
import com.iflytek.skillhub.domain.plugin.PluginPublicationTaskRepository;
import com.iflytek.skillhub.domain.plugin.PluginRelease;
import com.iflytek.skillhub.domain.plugin.PluginReleaseRepository;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.plugin.service.PluginReleaseWorkflow;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.ChannelView;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.DependencyUploadView;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.PrerequisitesView;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.RegistryRequest;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.RegistryView;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.SettingsView;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.PluginNpmQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.storage.ObjectStorageService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class PluginNpmManagementAppService {
    private final PluginNpmRegistryRepository registries;
    private final PluginNpmSelectionRepository selections;
    private final PluginNpmDependencyUploadRepository uploads;
    private final PluginPublicationChannelRepository channels;
    private final PluginPublicationTaskRepository tasks;
    private final PluginPublicationProperties properties;
    private final PluginNpmQueryRepository queries;
    private final PluginReleaseWorkflow workflow;
    private final PluginReleaseRepository releases;
    private final PluginDistributionRepository distributions;
    private final ObjectStorageService storage;
    private final OfflinePluginScanner scanner;
    private final PluginOfflineImportAppService imports;
    private final NpmRegistryPublisher publisher;
    private final PluginNpmPublicationAdapter adapter;
    private final PluginPublicationCoordinator coordinator;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final AuditLogService audit;
    private final RequestIdAccessor ids;

    public PluginNpmManagementAppService(
            PluginNpmRegistryRepository registries,
            PluginNpmSelectionRepository selections,
            PluginNpmDependencyUploadRepository uploads,
            PluginPublicationChannelRepository channels,
            PluginPublicationTaskRepository tasks,
            PluginPublicationProperties properties,
            PluginNpmQueryRepository queries,
            PluginReleaseWorkflow workflow,
            PluginReleaseRepository releases,
            PluginDistributionRepository distributions,
            ObjectStorageService storage,
            OfflinePluginScanner scanner,
            PluginOfflineImportAppService imports,
            NpmRegistryPublisher publisher,
            PluginNpmPublicationAdapter adapter,
            PluginPublicationCoordinator coordinator,
            ObjectMapper mapper,
            PlatformTransactionManager manager,
            AuditLogService audit,
            RequestIdAccessor ids) {
        this.registries = registries;
        this.selections = selections;
        this.uploads = uploads;
        this.channels = channels;
        this.tasks = tasks;
        this.properties = properties;
        this.queries = queries;
        this.workflow = workflow;
        this.releases = releases;
        this.distributions = distributions;
        this.storage = storage;
        this.scanner = scanner;
        this.imports = imports;
        this.publisher = publisher;
        this.adapter = adapter;
        this.coordinator = coordinator;
        this.mapper = mapper;
        tx = new TransactionTemplate(manager);
        this.audit = audit;
        this.ids = ids;
    }

    public void requireAdmin(PluginActor actor) {
        if (!actor.reviewer()) throw new DomainForbiddenException("error.plugin.permission");
    }

    public List<RegistryView> registries(PluginActor actor) {
        if (actor.userId() == null) throw new DomainForbiddenException("error.plugin.permission");
        return queries.settings().registries();
    }

    public SettingsView settings(PluginActor actor) {
        requireAdmin(actor);
        return queries.settings();
    }

    public RegistryView configure(
            RegistryRequest request, PluginActor actor, AuditRequestContext context) {
        requireAdmin(actor);
        String registry;
        try {
            registry = NpmRegistryPublisher.canonical(request.registry());
        } catch (Exception ex) {
            throw new DomainBadRequestException("error.plugin.npmRegistry");
        }
        final String target = registry;
        if (properties.getAllowedRegistries().stream()
                        .noneMatch(
                                value -> {
                                    try {
                                        return NpmRegistryPublisher.canonical(value).equals(target);
                                    } catch (Exception ex) {
                                        return false;
                                    }
                                })
                || !properties.getCredentials().containsKey(request.credentialRef()))
            throw new DomainBadRequestException("error.plugin.npmRegistry");
        return tx.execute(
                status -> {
                    if (request.defaultRegistry()) registries.clearDefaultRegistries();
                    var saved =
                            registries.save(
                                    new PluginNpmRegistry(
                                            request.name(),
                                            target,
                                            request.credentialRef(),
                                            request.defaultRegistry(),
                                            actor.userId()));
                    record("plugin.npm.configure", saved.getId(), actor, context);
                    return queries.registry(saved);
                });
    }

    public RegistryView select(
            Long releaseId, Long registryId, PluginActor actor, AuditRequestContext context) {
        return tx.execute(
                status -> {
                    var release = workflow.locked(releaseId);
                    workflow.requireWritable(
                            workflow.requireProject(release.getProjectId()), actor);
                    release.requireDraft();
                    var registry = registry(registryId);
                    selections.save(new PluginNpmSelection(releaseId, registryId));
                    record("plugin.npm.select", releaseId, actor, context);
                    return queries.registry(registry);
                });
    }

    public List<DependencyUploadView> uploads(PluginActor actor) {
        requireAdmin(actor);
        return queries.uploads();
    }

    public DependencyUploadView upload(
            Long registryId,
            String source,
            InputStream input,
            PluginActor actor,
            AuditRequestContext context)
            throws IOException {
        requireAdmin(actor);
        if (source == null || source.isBlank() || source.length() > 2000)
            throw new DomainBadRequestException("error.plugin.metadata");
        var registry = registry(registryId);
        Path archive = Files.createTempFile("astronhub-dependency-upload-", ".tgz");
        String key = "plugins/npm-dependencies/" + UUID.randomUUID() + ".tgz";
        boolean keep = false;
        try {
            try (var output = Files.newOutputStream(archive)) {
                PluginNpmPublicationAdapter.copyBounded(input, output);
            }
            try (var scan = scanner.scanDependency(archive, imports.limits())) {
                var candidate = scan.candidates().getFirst();
                try (var original = Files.newInputStream(archive)) {
                    storage.putObject(key, original, Files.size(archive), "application/gzip");
                }
                var target =
                        new NpmRegistryPublisher.Target(
                                registry.getId(),
                                registry.getRegistry(),
                                registry.getCredentialRef());
                var value =
                        new PluginNpmDependencyUpload(
                                actor.userId(),
                                source,
                                candidate.nativeName(),
                                candidate.version(),
                                candidate.sha256(),
                                key,
                                candidate.manifestJson(),
                                mapper.writeValueAsString(target));
                var result =
                        tx.execute(
                                status -> {
                                    var saved = uploads.save(value);
                                    record(
                                            "plugin.npm.dependency.upload",
                                            saved.getId(),
                                            actor,
                                            context);
                                    return queries.upload(saved);
                                });
                keep = true;
                return result;
            }
        } finally {
            Files.deleteIfExists(archive);
            if (!keep)
                try {
                    storage.deleteObject(key);
                } catch (RuntimeException ignored) {
                }
        }
    }

    public DependencyUploadView publishDependency(
            Long id, PluginActor actor, AuditRequestContext context) throws IOException {
        requireAdmin(actor);
        var claim =
                tx.execute(
                        status -> {
                            var value =
                                    uploads.findByIdForUpdate(id)
                                            .orElseThrow(
                                                    () ->
                                                            new DomainNotFoundException(
                                                                    "error.plugin.npmDependency"));
                            String token = value.confirm();
                            uploads.save(value);
                            record("plugin.npm.dependency.confirm", id, actor, context);
                            return new Claim(value, token);
                        });
        if (claim.token() == null) return queries.upload(claim.value());
        String receipt = null, error = null;
        Path archive = Files.createTempFile("astronhub-dependency-publish-", ".tgz");
        try {
            try (var input = storage.getObject(claim.value().getObjectKey());
                    var output = Files.newOutputStream(archive)) {
                PluginNpmPublicationAdapter.copyBounded(input, output);
            }
            if (!OfflinePluginScanner.sha256(archive).equals(claim.value().getSha256()))
                throw new NpmRegistryPublisher.Failure("PACKAGE_DIGEST_MISMATCH");
            var target =
                    mapper.readValue(
                            claim.value().getSnapshot(), NpmRegistryPublisher.Target.class);
            receipt =
                    mapper.writeValueAsString(
                            publisher.publish(
                                    archive,
                                    claim.value().getManifestJson(),
                                    target,
                                    properties.resolve(target.credentialRef())));
        } catch (Exception ex) {
            error =
                    ex instanceof NpmRegistryPublisher.Failure failure
                            ? failure.code()
                            : "DEPENDENCY_PUBLICATION_FAILED";
        } finally {
            Files.deleteIfExists(archive);
        }
        final String result = receipt, code = error;
        return tx.execute(
                status -> {
                    var value = uploads.findByIdForUpdate(id).orElseThrow();
                    value.finish(claim.token(), result, code);
                    uploads.save(value);
                    record(
                            code == null
                                    ? "plugin.npm.dependency.published"
                                    : "plugin.npm.dependency.failed",
                            id,
                            actor,
                            context);
                    return queries.upload(value);
                });
    }

    private record Claim(PluginNpmDependencyUpload value, String token) {}

    public List<ChannelView> channels(Long releaseId, PluginActor actor) {
        requireRead(releaseId, actor);
        return tasks.findByReleaseId(releaseId)
                .map(
                        task ->
                                channels.findByTaskIdOrderByChannel(task.getId()).stream()
                                        .map(
                                                value ->
                                                        new ChannelView(
                                                                value.getChannel(),
                                                                value.getState(),
                                                                value.getSnapshot(),
                                                                value.getAttemptCount(),
                                                                value.getLastError(),
                                                                value.getReceipt()))
                                        .toList())
                .orElse(List.of());
    }

    public List<ChannelView> retry(Long releaseId, PluginActor actor, AuditRequestContext context) {
        requireAdmin(actor);
        tx.executeWithoutResult(
                status -> record("plugin.publication.retry", releaseId, actor, context));
        coordinator.execute(releaseId);
        return channels(releaseId, actor);
    }

    public PrerequisitesView prerequisites(Long releaseId, PluginActor actor) throws IOException {
        var release = requireRead(releaseId, actor);
        var archives = distributions.findByReleaseIdOrderByRuntimeKey(releaseId);
        var pi =
                archives.stream()
                        .filter(d -> d.getRuntimeKey().equals("pi"))
                        .findFirst()
                        .orElseThrow(
                                () -> new DomainNotFoundException("error.plugin.archiveMissing"));
        String plan =
                tasks.findByReleaseId(releaseId)
                        .map(
                                task ->
                                        channels.findByTaskIdOrderByChannel(task.getId()).stream()
                                                .filter(c -> c.getChannel().equals("NPM"))
                                                .map(PluginPublicationChannel::getSnapshot)
                                                .findFirst()
                                                .orElse("MISSING_CONFIGURATION"))
                        .orElseGet(
                                () ->
                                        adapter.snapshot(
                                                workflow.requireProject(release.getProjectId()),
                                                release,
                                                archives));
        if (plan == null || plan.equals("MISSING_CONFIGURATION"))
            return new PrerequisitesView(
                    null, List.of(), pi.getManifestJson(), null, null, "UNKNOWN");
        var snapshot = mapper.readValue(plan, PluginNpmPublicationAdapter.Snapshot.class);
        var target = snapshot.target();
        return new PrerequisitesView(
                target.registry(),
                publisher.dependencies(
                        snapshot.manifestJson(),
                        target,
                        properties.resolve(target.credentialRef())),
                pi.getManifestJson(),
                "pi install npm:" + pi.getNativeName() + "@" + pi.getNativeVersion(),
                "npm_config_registry=" + target.registry(),
                "UNKNOWN");
    }

    private PluginRelease requireRead(Long id, PluginActor actor) {
        var release =
                releases.findById(id)
                        .orElseThrow(
                                () -> new DomainNotFoundException("error.plugin.releaseNotFound"));
        if (!workflow.canManageRead(workflow.requireProject(release.getProjectId()), actor))
            throw new DomainForbiddenException("error.plugin.permission");
        return release;
    }

    private PluginNpmRegistry registry(Long id) {
        return registries
                .findById(id)
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.npmRegistry"));
    }

    private void record(String action, Long id, PluginActor actor, AuditRequestContext context) {
        audit.record(
                actor.userId(),
                action,
                "PLUGIN_NPM",
                id,
                ids.current(),
                context.clientIp(),
                context.userAgent(),
                "{}");
    }
}
