package com.iflytek.skillhub.service.plugin;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.plugin.PluginDistribution;
import com.iflytek.skillhub.domain.plugin.PluginDistributionRepository;
import com.iflytek.skillhub.domain.plugin.PluginProject;
import com.iflytek.skillhub.domain.plugin.PluginPublicationTaskRepository;
import com.iflytek.skillhub.domain.plugin.PluginRelease;
import com.iflytek.skillhub.domain.plugin.PluginReleaseRepository;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.plugin.service.PluginLifecycleService;
import com.iflytek.skillhub.domain.plugin.service.PluginReleaseWorkflow;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.PluginDraftUpdateRequest;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.PluginProjectCreateRequest;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.PluginProjectUpdateRequest;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.PluginProjectView;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.PluginReleaseCreateRequest;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.PluginReleaseView;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.PluginReviewRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.PluginManagementQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.storage.ObjectStorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Orchestrates native validation/storage, domain rules, management projections, and audit. */
@Service
public class PluginManagementAppService {
    private static final Logger log = LoggerFactory.getLogger(PluginManagementAppService.class);
    private final PluginLifecycleService lifecycle;
    private final PluginReleaseWorkflow workflow;
    private final PluginReleaseRepository releases;
    private final PluginDistributionRepository distributions;
    private final PluginPublicationTaskRepository tasks;
    private final PluginManagementQueryRepository queries;
    private final ClaudePluginZipValidator validator;
    private final PluginOfflineImportAppService imports;
    private final ObjectStorageService storage;
    private final AuditLogService audit;
    private final RequestIdAccessor requestIds;
    private final PluginPublicationCoordinator coordinator;

    public PluginManagementAppService(
            PluginLifecycleService lifecycle,
            PluginReleaseWorkflow workflow,
            PluginReleaseRepository releases,
            PluginDistributionRepository distributions,
            PluginPublicationTaskRepository tasks,
            PluginManagementQueryRepository queries,
            ClaudePluginZipValidator validator,
            ObjectStorageService storage,
            AuditLogService audit,
            RequestIdAccessor requestIds,
            PluginOfflineImportAppService imports,
            PluginPublicationCoordinator coordinator) {
        this.coordinator = coordinator;
        this.lifecycle = lifecycle;
        this.workflow = workflow;
        this.releases = releases;
        this.distributions = distributions;
        this.tasks = tasks;
        this.queries = queries;
        this.validator = validator;
        this.storage = storage;
        this.audit = audit;
        this.requestIds = requestIds;
        this.imports = imports;
    }

    @Transactional
    public PluginProjectView create(
            PluginProjectCreateRequest request, PluginActor actor, AuditRequestContext context) {
        PluginProject project =
                lifecycle.createProject(
                        request.namespaceId(),
                        request.slug(),
                        request.displayName(),
                        request.summary(),
                        request.visibility(),
                        actor);
        record("plugin.project.create", project.getId(), actor, context);
        return queries.detail(project.getId(), actor);
    }

    @Transactional
    public PluginProjectView update(
            Long id,
            PluginProjectUpdateRequest request,
            PluginActor actor,
            AuditRequestContext context) {
        workflow.updateProject(id, request.displayName(), request.summary(), actor);
        record("plugin.project.update", id, actor, context);
        return queries.detail(id, actor);
    }

    @Transactional
    public PluginReleaseView createRelease(
            Long id,
            PluginReleaseCreateRequest request,
            PluginActor actor,
            AuditRequestContext context) {
        PluginRelease release =
                workflow.createRelease(id, request.version(), request.changelog(), actor);
        record("plugin.release.create", release.getId(), actor, context);
        return queries.release(release.getId(), actor);
    }

    @Transactional
    public PluginReleaseView updateDraft(
            Long id,
            PluginDraftUpdateRequest request,
            PluginActor actor,
            AuditRequestContext context) {
        workflow.updateDraft(id, request.changelog(), actor);
        record("plugin.release.update", id, actor, context);
        return queries.release(id, actor);
    }

    @Transactional
    public PluginReleaseView importArchive(
            Long id, InputStream input, PluginActor actor, AuditRequestContext context)
            throws IOException {
        PluginRelease release = workflow.locked(id);
        workflow.requireWritable(workflow.requireProject(release.getProjectId()), actor);
        release.requireDraft();
        if (distributions.findByReleaseIdOrderByRuntimeKey(id).stream()
                .anyMatch(d -> "claude-code".equals(d.getRuntimeKey()))) {
            throw new DomainBadRequestException("error.plugin.archiveConflict");
        }
        var limits = imports.limits();
        var configuredValidator =
                new ClaudePluginZipValidator(
                        Math.min(limits.packageBytes(), 104857600L),
                        limits.expandedBytes(),
                        limits.files());
        byte[] bytes = configuredValidator.readBounded(input);
        var validated = configuredValidator.validate(bytes, release.getVersion());
        String objectKey =
                "plugins/archives/" + UUID.randomUUID() + "/" + validated.sha256() + ".zip";
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            try {
                                storage.deleteObject(objectKey);
                            } catch (RuntimeException ex) {
                                log.warn("Plugin archive cleanup failed after rollback");
                            }
                        }
                    }
                });
        storage.putObject(
                objectKey, new ByteArrayInputStream(bytes), bytes.length, "application/zip");
        workflow.attachArchive(
                id,
                objectKey,
                validated.sha256(),
                validated.nativeName(),
                validated.nativeVersion(),
                validated.inventoryJson(),
                validated.manifestJson(),
                actor);
        record("plugin.archive.import", id, actor, context);
        return queries.release(id, actor);
    }

    @Transactional
    public PluginReleaseView submit(Long id, PluginActor actor, AuditRequestContext context) {
        workflow.submit(id, actor);
        record("plugin.review.submit", id, actor, context);
        return queries.release(id, actor);
    }

    @Transactional
    public PluginReleaseView review(
            Long id,
            PluginReviewRequest request,
            boolean approve,
            boolean direct,
            PluginActor actor,
            AuditRequestContext context) {
        if (approve)
            coordinator.prepare(
                    workflow.approve(
                            id,
                            request.notes(),
                            request.runtimeTestResult(),
                            request.testEnvironment(),
                            actor,
                            direct));
        else
            workflow.reject(
                    id,
                    request.notes(),
                    request.runtimeTestResult(),
                    request.testEnvironment(),
                    actor);
        record(
                direct
                        ? "plugin.review.directApprove"
                        : approve ? "plugin.review.approve" : "plugin.review.reject",
                id,
                actor,
                context);
        return queries.release(id, actor);
    }

    public PluginReleaseView publishHosted(
            Long id, PluginActor actor, AuditRequestContext context) {
        if (!actor.reviewer()) throw new DomainForbiddenException("error.plugin.permission");
        coordinator.executeHosted(id, true);
        return queries.release(id, actor);
    }

    public void executeHosted(Long id, PluginActor actor, AuditRequestContext context) {
        coordinator.executeHosted(id, false);
    }

    @Transactional
    public Download openArchive(
            Long id,
            String runtime,
            boolean inspection,
            PluginActor actor,
            AuditRequestContext context) {
        PluginRelease release =
                releases.findById(id)
                        .orElseThrow(
                                () -> new DomainNotFoundException("error.plugin.releaseNotFound"));
        workflow.requireDownload(release, actor, inspection);
        PluginDistribution archive =
                distributions.findByReleaseIdOrderByRuntimeKey(id).stream()
                        .filter(d -> d.getRuntimeKey().equals(runtime))
                        .findFirst()
                        .orElseThrow(
                                () -> new DomainNotFoundException("error.plugin.archiveMissing"));
        record(
                inspection ? "plugin.archive.inspect" : "plugin.archive.download",
                id,
                actor,
                context);
        return new Download(
                storage.getObject(archive.getSourceLocator()),
                workflow.requireProject(release.getProjectId()).getSlug()
                        + "-"
                        + release.getVersion()
                        + (archive.getSourceLocator().endsWith(".tgz") ? ".tgz" : ".zip"),
                archive.getIntegrityDigest());
    }

    private void record(String action, Long id, PluginActor actor, AuditRequestContext context) {
        audit.record(
                actor.userId(),
                action,
                action.startsWith("plugin.project.") ? "PLUGIN_PROJECT" : "PLUGIN_RELEASE",
                id,
                requestIds.current(),
                context.clientIp(),
                context.userAgent(),
                "{}");
    }

    public record Download(InputStream stream, String filename, String sha256) {}
}
