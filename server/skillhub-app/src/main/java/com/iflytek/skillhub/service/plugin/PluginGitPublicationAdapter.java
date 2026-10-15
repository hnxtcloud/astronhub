package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.PluginPublicationProperties;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.plugin.PluginDistribution;
import com.iflytek.skillhub.domain.plugin.PluginGitNamespaceTargetRepository;
import com.iflytek.skillhub.domain.plugin.PluginGitPublicationIntent;
import com.iflytek.skillhub.domain.plugin.PluginGitPublicationIntentRepository;
import com.iflytek.skillhub.domain.plugin.PluginGitTargetRepository;
import com.iflytek.skillhub.domain.plugin.PluginProject;
import com.iflytek.skillhub.domain.plugin.PluginRelease;
import com.iflytek.skillhub.domain.plugin.PluginVisibility;
import com.iflytek.skillhub.storage.ObjectStorageService;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class PluginGitPublicationAdapter implements PluginPublicationChannelAdapter {
    private final PluginGitTargetRepository targets;
    private final PluginGitNamespaceTargetRepository bindings;
    private final PluginGitPublicationIntentRepository intents;
    private final NamespaceRepository namespaces;
    private final PluginPublicationProperties properties;
    private final PluginOfflineImportAppService imports;
    private final OfflinePluginScanner scanner;
    private final ObjectStorageService storage;
    private final GitMarketplacePublisher publisher;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public PluginGitPublicationAdapter(
            PluginGitTargetRepository targets,
            PluginGitNamespaceTargetRepository bindings,
            PluginGitPublicationIntentRepository intents,
            NamespaceRepository namespaces,
            PluginPublicationProperties properties,
            PluginOfflineImportAppService imports,
            OfflinePluginScanner scanner,
            ObjectStorageService storage,
            GitMarketplacePublisher publisher,
            ObjectMapper mapper,
            PlatformTransactionManager manager) {
        this.targets = targets;
        this.bindings = bindings;
        this.intents = intents;
        this.namespaces = namespaces;
        this.properties = properties;
        this.imports = imports;
        this.scanner = scanner;
        this.storage = storage;
        this.publisher = publisher;
        this.mapper = mapper;
        tx = new TransactionTemplate(manager);
    }

    public record Archive(
            Long distributionId,
            String runtime,
            String name,
            String version,
            String sha256,
            String key) {}

    public record Snapshot(
            GitMarketplacePublisher.Target target,
            String namespace,
            Long projectId,
            Long releaseId,
            long timestamp,
            PluginImportLimits limits,
            List<Archive> archives) {}

    public String channel() {
        return "GIT";
    }

    public String snapshot(
            PluginProject project, PluginRelease release, List<PluginDistribution> distributions) {
        if (project.getVisibility() != PluginVisibility.PUBLIC) return null;
        var target =
                bindings.findById(project.getNamespaceId())
                        .flatMap(b -> targets.findById(b.getTargetId()))
                        .orElse(null);
        if (target == null || !properties.available(target.getCredentialRef())) return null;
        var namespace = namespaces.findById(project.getNamespaceId()).orElseThrow();
        var archives =
                distributions.stream()
                        .filter(d -> Set.of("claude-code", "codex").contains(d.getRuntimeKey()))
                        .map(
                                d ->
                                        new Archive(
                                                d.getId(),
                                                d.getRuntimeKey(),
                                                d.getNativeName(),
                                                d.getNativeVersion(),
                                                d.getIntegrityDigest(),
                                                d.getSourceLocator()))
                        .toList();
        if (archives.isEmpty()) return null;
        try {
            return mapper.writeValueAsString(
                    new Snapshot(
                            new GitMarketplacePublisher.Target(
                                    target.getId(),
                                    target.getRemote(),
                                    target.getFetchUrl(),
                                    target.getBranch(),
                                    target.getCredentialRef()),
                            namespace.getSlug(),
                            project.getId(),
                            release.getId(),
                            release.getCreatedAt().getEpochSecond(),
                            imports.limits(),
                            archives));
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
        return execute(context, inspect, null);
    }

    public java.util.Optional<String> withdraw(
            PluginChannelContext context, Long operationId, boolean inspect, Runnable beforeWrite)
            throws Exception {
        try (var guard = PluginPublicationWriteGuard.bind(beforeWrite)) {
            return execute(
                    context,
                    inspect,
                    new GitMarketplacePublisher.Withdrawal(
                            operationId, context.taskId(), context.releaseId()));
        }
    }

    private java.util.Optional<String> execute(
            PluginChannelContext context,
            boolean inspect,
            GitMarketplacePublisher.Withdrawal withdrawal)
            throws Exception {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive())
            throw new GitMarketplacePublisher.Failure("GIT_TRANSACTION_BOUNDARY");
        Snapshot plan = mapper.readValue(context.snapshot(), Snapshot.class);
        if (!plan.releaseId().equals(context.releaseId()))
            throw new GitMarketplacePublisher.Failure("GIT_SNAPSHOT_MISMATCH");
        Path staging = Files.createTempDirectory("astronhub-git-source-");
        List<OfflinePluginScanner.NativeFiles> extracted = new ArrayList<>();
        try {
            List<GitMarketplacePublisher.Artifact> artifacts = new ArrayList<>();
            int index = 0;
            for (Archive archive : plan.archives()) {
                if (withdrawal != null) {
                    artifacts.add(
                            new GitMarketplacePublisher.Artifact(
                                    archive.runtime(),
                                    archive.name(),
                                    archive.version(),
                                    archive.sha256(),
                                    Map.of()));
                    continue;
                }
                Path path = staging.resolve("archive-" + (++index) + ".zip");
                try (var input = storage.getObject(archive.key());
                        var output = Files.newOutputStream(path)) {
                    PluginNpmPublicationAdapter.copyBounded(input, output);
                }
                if (!OfflinePluginScanner.sha256(path).equals(archive.sha256()))
                    throw new GitMarketplacePublisher.Failure("GIT_ARCHIVE_DIGEST");
                var files = scanner.openNativeFiles(path, plan.limits());
                extracted.add(files);
                Map<String, GitMarketplacePublisher.Blob> blobs = new TreeMap<>();
                files.files()
                        .forEach(
                                (name, file) ->
                                        blobs.put(
                                                name,
                                                new GitMarketplacePublisher.Blob(
                                                        file,
                                                        (files.modes().get(name) & 0111) != 0)));
                artifacts.add(
                        new GitMarketplacePublisher.Artifact(
                                archive.runtime(),
                                archive.name(),
                                archive.version(),
                                archive.sha256(),
                                blobs));
            }
            var store =
                    new GitMarketplacePublisher.IntentStore() {
                        public List<GitMarketplacePublisher.Intent> previous() {
                            return intents.findByTaskIdOrderByIdDesc(context.taskId()).stream()
                                    .map(
                                            value ->
                                                    new GitMarketplacePublisher.Intent(
                                                            value.getParentSha(),
                                                            value.getTreeSha(),
                                                            value.getCommitSha(),
                                                            value.getPlanDigest()))
                                    .toList();
                        }

                        public void record(GitMarketplacePublisher.Intent value) {
                            tx.executeWithoutResult(
                                    status -> {
                                        if (intents.findByTaskIdAndCommitSha(
                                                        context.taskId(), value.commit())
                                                .isEmpty())
                                            intents.save(
                                                    new PluginGitPublicationIntent(
                                                            context.taskId(),
                                                            value.parent(),
                                                            value.tree(),
                                                            value.commit(),
                                                            value.planDigest()));
                                    });
                        }
                    };
            var approved =
                    new GitMarketplacePublisher.Plan(
                            plan.target(),
                            plan.namespace(),
                            plan.projectId(),
                            plan.releaseId(),
                            context.taskId(),
                            plan.timestamp(),
                            artifacts,
                            withdrawal);
            var receipt =
                    inspect
                            ? publisher.reconcile(
                                    approved,
                                    properties.resolve(plan.target().credentialRef()),
                                    store)
                            : java.util.Optional.of(
                                    publisher.publish(
                                            approved,
                                            properties.resolve(plan.target().credentialRef()),
                                            store));
            return receipt.map(
                    value -> {
                        try {
                            return mapper.writeValueAsString(value);
                        } catch (java.io.IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    });
        } finally {
            for (var files : extracted) files.close();
            OfflinePluginScanner.cleanup(staging);
        }
    }
}
