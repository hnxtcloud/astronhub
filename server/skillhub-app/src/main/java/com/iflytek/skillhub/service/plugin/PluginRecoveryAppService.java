package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.PluginPublicationProperties;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.plugin.service.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.dto.plugin.PluginRecoveryDtos.*;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.PluginRecoveryQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class PluginRecoveryAppService {
    private final PluginChannelGovernanceOperationRepository operations;
    private final PluginReleaseWorkflow workflow;
    private final PluginProjectRepository projects;
    private final PluginPublicationTaskRepository tasks;
    private final PluginPublicationChannelRepository channels;
    private final PluginChannelPlanRevisionRepository plans;
    private final PluginDistributionRepository distributions;
    private final PluginNpmRegistryRepository registries;
    private final PluginGitTargetRepository targets;
    private final NamespaceRepository namespaces;
    private final PluginPublicationProperties properties;
    private final PluginOfflineImportAppService imports;
    private final ObjectMapper mapper;
    private final PluginRecoveryQueryRepository queries;
    private final AuditLogService audit;
    private final RequestIdAccessor ids;
    private final TransactionTemplate tx;
    private final PluginChannelAttemptRunner runner;

    public PluginRecoveryAppService(
            PluginReleaseWorkflow workflow,
            PluginProjectRepository projects,
            PluginPublicationTaskRepository tasks,
            PluginPublicationChannelRepository channels,
            PluginChannelPlanRevisionRepository plans,
            PluginDistributionRepository distributions,
            PluginNpmRegistryRepository registries,
            PluginGitTargetRepository targets,
            NamespaceRepository namespaces,
            PluginPublicationProperties properties,
            PluginOfflineImportAppService imports,
            ObjectMapper mapper,
            PluginRecoveryQueryRepository queries,
            AuditLogService audit,
            RequestIdAccessor ids,
            PluginChannelGovernanceOperationRepository operations, TransactionTemplate tx, PluginChannelAttemptRunner runner) {
        this.operations = operations;
        this.tx = tx;
        this.runner = runner;
        this.workflow = workflow;
        this.projects = projects;
        this.tasks = tasks;
        this.channels = channels;
        this.plans = plans;
        this.distributions = distributions;
        this.registries = registries;
        this.targets = targets;
        this.namespaces = namespaces;
        this.properties = properties;
        this.imports = imports;
        this.mapper = mapper;
        this.queries = queries;
        this.audit = audit;
        this.ids = ids;
    }

    private PluginRelease eligible(Long id, PluginActor actor) {
        if (!actor.reviewer()) throw new DomainForbiddenException("error.plugin.permission");
        var release = workflow.locked(id);
        var p = projects.findByIdForUpdate(release.getProjectId()).orElseThrow();
        workflow.requireActive(p);
        if (p.isHidden()
                || p.getVisibility() != PluginVisibility.PUBLIC
                || release.getStatus() != PluginReleaseStatus.PENDING_REVIEW)
            throw new DomainBadRequestException("error.plugin.recoveryState");
        return release;
    }

    @Transactional
    public ReplanPreview preview(Long id, ReplanRequest request, PluginActor actor) {
        var release = eligible(id, actor);
        return preview(release, request);
    }

    private ReplanPreview preview(PluginRelease release, ReplanRequest request) {
        var task = tasks.findByReleaseId(release.getId()).orElseThrow();
        if (!Set.of(task.getRequiredChannels().split(",")).contains(request.channel())
                || request.channel().equals("HOSTED"))
            throw new DomainBadRequestException("error.plugin.recoveryState");
        var found =
                channels.findByTaskIdOrderByChannel(task.getId()).stream()
                        .filter(c -> c.getChannel().equals(request.channel()))
                        .findFirst();
        if (found.isPresent()
                && (found.get().getPlanRevision() != request.expectedRevision()
                        || !Set.of("BLOCKED", "FAILED").contains(found.get().getState())
                        || found.get().isSourceDisabled()))
            throw new DomainBadRequestException("error.plugin.recoveryState");
        if (found.isEmpty() && request.expectedRevision() != 0)
            throw new DomainBadRequestException("error.plugin.recoveryConflict");
        String snapshot =
                explicitSnapshot(workflow.requireProject(release.getProjectId()), release, request);
        String digest =
                sha(
                        request.channel()
                                + "\n"
                                + request.expectedRevision()
                                + "\n"
                                + request.reason()
                                + "\n"
                                + snapshot);
        return new ReplanPreview(request.channel(), request.expectedRevision(), snapshot, digest);
    }

    @Transactional
    public List<RecoveryChannelView> apply(
            Long id, ReplanRequest request, PluginActor actor, AuditRequestContext context) {
        var release = eligible(id, actor);
        var candidate = preview(release, request);
        if (!candidate.digest().equals(request.previewDigest()))
            throw new DomainBadRequestException("error.plugin.recoveryConflict");
        var task = tasks.findByReleaseId(id).orElseThrow();
        var c =
                channels.findByTaskIdOrderByChannel(task.getId()).stream()
                        .filter(v -> v.getChannel().equals(request.channel()))
                        .findFirst()
                        .orElseGet(
                                () ->
                                        channels.save(
                                                new PluginPublicationChannel(
                                                        task.getId(), request.channel(), null)));
        c = channels.findByIdForUpdate(c.getId()).orElseThrow();
        if (plans.findByChannelIdOrderByRevisionAsc(c.getId()).isEmpty())
            plans.save(
                    new PluginChannelPlanRevision(
                            c.getId(),
                            0,
                            c.getSnapshot(),
                            task.getApprovedBy(),
                            "ORIGINAL_APPROVAL",
                            task.getCreatedAt()));
        c.replan(request.expectedRevision(), candidate.snapshot());
        channels.save(c);
        plans.save(
                new PluginChannelPlanRevision(
                        c.getId(),
                        c.getPlanRevision(),
                        candidate.snapshot(),
                        actor.userId(),
                        request.reason().trim(),
                        Instant.now()));
        audit.record(
                actor.userId(),
                "plugin.publication.replan",
                "PLUGIN_RELEASE",
                id,
                ids.current(),
                context.clientIp(),
                context.userAgent(),
                "{\"channel\":\""
                        + request.channel()
                        + "\",\"revision\":"
                        + c.getPlanRevision()
                        + "}");
        return queries.read(id, actor);
    }

    private String explicitSnapshot(PluginProject p, PluginRelease r, ReplanRequest request) {
        try {
            var archives = distributions.findByReleaseIdOrderByRuntimeKey(r.getId());
            if (request.channel().equals("NPM")) {
                var target = registries.findById(request.targetRevisionId()).orElseThrow();
                if (!properties.getAllowedRegistries().stream()
                                .map(NpmRegistryPublisher::canonical)
                                .toList()
                                .contains(NpmRegistryPublisher.canonical(target.getRegistry()))
                        || !properties.available(target.getCredentialRef()))
                    throw new IllegalArgumentException();
                var pi =
                        archives.stream()
                                .filter(d -> d.getRuntimeKey().equals("pi"))
                                .findFirst()
                                .orElseThrow();
                if (!mapper.readTree(pi.getInventoryJson())
                        .path("packaging")
                        .asText()
                        .equals("ORIGINAL_NPM_TGZ")) throw new IllegalArgumentException();
                return mapper.writeValueAsString(
                        new PluginNpmPublicationAdapter.Snapshot(
                                pi.getId(),
                                "pi-package-json",
                                new NpmRegistryPublisher.Target(
                                        target.getId(),
                                        target.getRegistry(),
                                        target.getCredentialRef()),
                                pi.getSourceLocator(),
                                pi.getIntegrityDigest(),
                                pi.getManifestJson(),
                                pi.getInventoryJson()));
            }
            if (request.channel().equals("GIT")) {
                var target = targets.findById(request.targetRevisionId()).orElseThrow();
                if (!p.getNamespaceId().equals(target.getNamespaceId())
                        || !properties.getAllowedGitRemotes().contains(target.getRemote())
                        || !properties.available(target.getCredentialRef()))
                    throw new IllegalArgumentException();
                var selected =
                        archives.stream()
                                .filter(
                                        d ->
                                                Set.of("claude-code", "codex")
                                                        .contains(d.getRuntimeKey()))
                                .map(
                                        d ->
                                                new PluginGitPublicationAdapter.Archive(
                                                        d.getId(),
                                                        d.getRuntimeKey(),
                                                        d.getNativeName(),
                                                        d.getNativeVersion(),
                                                        d.getIntegrityDigest(),
                                                        d.getSourceLocator()))
                                .toList();
                if (selected.isEmpty()) throw new IllegalArgumentException();
                return mapper.writeValueAsString(
                        new PluginGitPublicationAdapter.Snapshot(
                                new GitMarketplacePublisher.Target(
                                        target.getId(),
                                        target.getRemote(),
                                        target.getFetchUrl(),
                                        target.getBranch(),
                                        target.getCredentialRef()),
                                namespaces.findById(p.getNamespaceId()).orElseThrow().getSlug(),
                                p.getId(),
                                r.getId(),
                                r.getCreatedAt().getEpochSecond(),
                                imports.limits(),
                                selected));
            }
            throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw new DomainBadRequestException("error.plugin.recoveryTarget");
        }
    }

    private PluginPublicationChannel governanceChannel(
            Long id, GovernanceRequest request, PluginActor actor) {
        if (!actor.reviewer()) throw new DomainForbiddenException("error.plugin.permission");
        var release = workflow.locked(id);
        var task = tasks.findByReleaseId(release.getId()).orElseThrow();
        if (!request.action().equals("WITHDRAW_CURRENT")
                || !Set.of("NPM", "GIT").contains(request.channel()))
            throw new DomainBadRequestException("error.plugin.recoveryState");
        var c =
                channels.findByTaskIdOrderByChannel(task.getId()).stream()
                        .filter(v -> v.getChannel().equals(request.channel()))
                        .findFirst()
                        .orElseThrow(
                                () -> new DomainBadRequestException("error.plugin.recoveryState"));
        c = channels.findByIdForUpdate(c.getId()).orElseThrow();
        if (c.getPlanRevision() != request.expectedRevision() || !c.getState().equals("SUCCEEDED"))
            throw new DomainBadRequestException("error.plugin.recoveryState");
        return c;
    }

    @Transactional
    public GovernancePreview previewGovernance(
            Long id, GovernanceRequest request, PluginActor actor) {
        var c = governanceChannel(id, request, actor);
        return governancePreview(c, request);
    }

    private GovernancePreview governancePreview(
            PluginPublicationChannel c, GovernanceRequest request) {
        String effect =
                c.getChannel().equals("NPM")
                        ? "REMOTE_DELETION_UNSUPPORTED; REMOTE_PACKAGE_RETAINED;"
                                + " LOCAL_SOURCE_DISABLED"
                        : "REMOVE_CURRENT_INDEX_ONLY; RETAIN_GIT_HISTORY_AND_VERSION_FILES;"
                                + " LOCAL_SOURCE_DISABLED";
        return new GovernancePreview(
                c.getChannel(),
                request.action(),
                c.getPlanRevision(),
                effect,
                sha(
                        c.getId()
                                + "\n"
                                + c.getPlanRevision()
                                + "\n"
                                + c.getEffectiveSnapshot()
                                + "\n"
                                + request.action()
                                + "\n"
                                + request.reason()
                                + "\n"
                                + effect));
    }

    @Transactional
    public List<RecoveryChannelView> applyGovernance(
            Long id, GovernanceRequest request, PluginActor actor, AuditRequestContext context) {
        var c = governanceChannel(id, request, actor);
        var preview = governancePreview(c, request);
        if (!preview.digest().equals(request.previewDigest()))
            throw new DomainBadRequestException("error.plugin.recoveryConflict");
        var previous = operations.findByChannelIdOrderByIdDesc(c.getId());
        if (previous.stream().noneMatch(o -> o.getPreviewDigest().equals(preview.digest()))) {
            if (previous.stream()
                    .anyMatch(
                            o ->
                                    Set.of("QUEUED", "RUNNING", "FAILED", "UNKNOWN")
                                            .contains(o.getState())))
                throw new DomainBadRequestException("error.plugin.recoveryState");
            var op =
                    new PluginChannelGovernanceOperation(
                            c, id, actor.userId(), request.reason().trim(), preview.digest());
            if (c.getChannel().equals("NPM")) op.unsupported();
            operations.save(op);
            c.disableSource();
            channels.save(c);
            audit.record(
                    actor.userId(),
                    "plugin.channel.withdraw.request",
                    "PLUGIN_RELEASE",
                    id,
                    ids.current(),
                    context.clientIp(),
                    context.userAgent(),
                    "{\"channel\":\"" + request.channel() + "\"}");
        }
        return queries.read(id, actor);
    }

    public List<RecoveryChannelView> retryGovernance(
            Long id, Long operationId, PluginActor actor, AuditRequestContext context) {
        tx.executeWithoutResult(status -> queueGovernanceRetry(id, operationId, actor, context));
        runner.executeGovernance(id, operationId);
        return queries.read(id, actor);
    }

    private void queueGovernanceRetry(
            Long id, Long operationId, PluginActor actor, AuditRequestContext context) {
        if (!actor.reviewer()) throw new DomainForbiddenException("error.plugin.permission");
        workflow.locked(id);
        var op = operations.findByIdForUpdate(operationId).orElseThrow();
        if (!op.getReleaseId().equals(id))
            throw new DomainBadRequestException("error.plugin.recoveryState");
        op.retryNow();
        operations.save(op);
        audit.record(
                actor.userId(),
                "plugin.channel.withdraw.retry",
                "PLUGIN_RELEASE",
                id,
                ids.current(),
                context.clientIp(),
                context.userAgent(),
                "{}");
    }

    private static String sha(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
