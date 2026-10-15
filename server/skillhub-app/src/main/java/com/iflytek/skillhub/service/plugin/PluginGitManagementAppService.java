package com.iflytek.skillhub.service.plugin;

import com.iflytek.skillhub.config.PluginPublicationProperties;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.plugin.PluginPublicationTaskRepository;
import com.iflytek.skillhub.domain.plugin.PluginReleaseRepository;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.plugin.service.PluginGitTargetWorkflow;
import com.iflytek.skillhub.domain.plugin.service.PluginReleaseWorkflow;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitIntentView;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitSettingsView;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitTargetRequest;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitTargetView;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.PluginGitQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Validates deployment transport policy, then orchestrates domain configuration and audit. */
@Service
public class PluginGitManagementAppService {
    private final PluginGitTargetWorkflow targetWorkflow;
    private final PluginPublicationTaskRepository tasks;
    private final PluginReleaseRepository releases;
    private final PluginReleaseWorkflow workflow;
    private final PluginPublicationProperties properties;
    private final PluginGitQueryRepository queries;
    private final AuditLogService audit;
    private final RequestIdAccessor requestIds;
    private final TransactionTemplate tx;

    public PluginGitManagementAppService(
            PluginGitTargetWorkflow targetWorkflow,
            PluginPublicationTaskRepository tasks,
            PluginReleaseRepository releases,
            PluginReleaseWorkflow workflow,
            PluginPublicationProperties properties,
            PluginGitQueryRepository queries,
            AuditLogService audit,
            RequestIdAccessor requestIds,
            PlatformTransactionManager manager) {
        this.targetWorkflow = targetWorkflow;
        this.tasks = tasks;
        this.releases = releases;
        this.workflow = workflow;
        this.properties = properties;
        this.queries = queries;
        this.audit = audit;
        this.requestIds = requestIds;
        tx = new TransactionTemplate(manager);
    }

    private void requireAdmin(PluginActor actor) {
        if (!actor.reviewer()) throw new DomainForbiddenException("error.plugin.permission");
    }

    public GitSettingsView settings(PluginActor actor) {
        requireAdmin(actor);
        return queries.settings();
    }

    public GitTargetView configure(
            GitTargetRequest request, PluginActor actor, AuditRequestContext context) {
        requireAdmin(actor);
        String remote, fetch;
        try {
            remote = GitMarketplacePublisher.canonicalRemote(request.remote());
            fetch = GitMarketplacePublisher.canonicalRemote(request.fetchUrl());
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.plugin.gitTarget");
        }
        if (!remote.equals(fetch)
                || !GitMarketplacePublisher.validBranch(request.branch())
                || !properties.getAllowedGitRemotes().contains(remote)
                || !properties.getAllowedGitRemotes().contains(fetch)
                || !properties.available(request.credentialRef()))
            throw new DomainBadRequestException("error.plugin.gitTarget");
        final String key;
        try {
            key =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    (remote + "\n" + request.branch())
                                                            .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
        return tx.execute(
                status -> {
                    var saved =
                            targetWorkflow.configure(
                                    request.namespaceId(),
                                    remote,
                                    fetch,
                                    request.branch(),
                                    request.credentialRef(),
                                    key,
                                    actor);
                    audit.record(
                            actor.userId(),
                            "plugin.git.configure",
                            "PLUGIN_NAMESPACE",
                            request.namespaceId(),
                            requestIds.current(),
                            context.clientIp(),
                            context.userAgent(),
                            "{}");
                    return queries.target(saved);
                });
    }

    public List<GitIntentView> intents(Long releaseId, PluginActor actor) {
        var release =
                releases.findById(releaseId)
                        .orElseThrow(
                                () -> new DomainNotFoundException("error.plugin.releaseNotFound"));
        if (!workflow.canManageRead(workflow.requireProject(release.getProjectId()), actor))
            throw new DomainForbiddenException("error.plugin.permission");
        return tasks.findByReleaseId(releaseId)
                .map(task -> queries.intents(task.getId()))
                .orElse(List.of());
    }
}
