package com.iflytek.skillhub.domain.plugin.service;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.shared.exception.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Catalog governance and non-owning component links never mutate approved native package bytes. */
@Service
public class PluginGovernanceWorkflow {
    private final PluginProjectRepository projects;
    private final PluginReleaseRepository releases;
    private final PluginPublicationTaskRepository tasks;
    private final NamespaceRepository namespaces;
    private final PluginReleaseWorkflow workflow;
    private final PluginComponentLinkRepository links;

    public PluginGovernanceWorkflow(
            PluginProjectRepository projects,
            PluginReleaseRepository releases,
            PluginPublicationTaskRepository tasks,
            NamespaceRepository namespaces,
            PluginReleaseWorkflow workflow,
            PluginComponentLinkRepository links) {
        this.projects = projects;
        this.releases = releases;
        this.tasks = tasks;
        this.namespaces = namespaces;
        this.workflow = workflow;
        this.links = links;
    }

    public boolean canMaintain(PluginProject project, PluginActor actor) {
        return actor.userId() != null
                && (actor.superAdmin()
                        || actor.namespaceAdmin(project.getNamespaceId())
                        || (actor.userId().equals(project.getOwnerId())
                                && actor.namespaceRoles().containsKey(project.getNamespaceId())));
    }

    public boolean canGovern(PluginProject project, PluginActor actor) {
        return actor.userId() != null && (actor.reviewer() || canMaintain(project, actor));
    }

    public PluginProject lockMaintained(Long id, PluginActor actor) {
        var project =
                projects.findByIdForUpdate(id)
                        .orElseThrow(() -> new DomainNotFoundException("error.plugin.notFound"));
        workflow.requireWritable(project, actor);
        return project;
    }

    @Transactional
    public PluginProject govern(
            Long id, String action, PluginVisibility visibility, String reason, PluginActor actor) {
        var project =
                projects.findByIdForUpdate(id)
                        .orElseThrow(() -> new DomainNotFoundException("error.plugin.notFound"));
        if (!canGovern(project, actor))
            throw new DomainForbiddenException("error.plugin.permission");
        requireReason(reason);
        if (Set.of("SHOW", "RESTORE", "VISIBILITY").contains(action)
                && namespaces.findById(project.getNamespaceId()).orElseThrow().getStatus()
                        != NamespaceStatus.ACTIVE)
            throw new DomainBadRequestException("error.plugin.namespaceInactive");
        switch (action) {
            case "HIDE" -> project.hide();
            case "SHOW" -> project.show();
            case "ARCHIVE" -> project.archive();
            case "RESTORE" -> project.restore();
            case "VISIBILITY" -> {
                if (!canMaintain(project, actor))
                    throw new DomainForbiddenException("error.plugin.permission");
                if (visibility == null)
                    throw new DomainBadRequestException("error.plugin.metadata");
                if (releases.findByProjectIdOrderByCreatedAtDesc(id).stream()
                        .anyMatch(
                                r ->
                                        tasks.findByReleaseId(r.getId())
                                                .filter(t -> !"SUCCEEDED".equals(t.getStatus()))
                                                .isPresent()))
                    throw new DomainBadRequestException("error.plugin.publicationPending");
                project.changeVisibility(visibility);
            }
            default -> throw new DomainBadRequestException("error.plugin.governanceAction");
        }
        project.setUpdatedBy(actor.userId());
        return projects.save(project);
    }

    @Transactional
    public PluginRelease yank(Long releaseId, String reason, PluginActor actor) {
        var release = workflow.locked(releaseId);
        var project =
                projects.findByIdForUpdate(release.getProjectId())
                        .orElseThrow(() -> new DomainNotFoundException("error.plugin.notFound"));
        if (!canGovern(project, actor))
            throw new DomainForbiddenException("error.plugin.permission");
        requireReason(reason);
        release.yank(actor.userId(), reason.trim());
        releases.save(release);
        workflow.refreshPublishedSelection(project);
        return release;
    }

    @Transactional
    public PluginComponentLink link(Long id, String kind, Long targetId, PluginActor actor) {
        var project = lockMaintained(id, actor);
        if (!Set.of("SKILL", "MCP").contains(kind) || targetId == null)
            throw new DomainBadRequestException("error.plugin.componentUnavailable");
        return links.findByProjectIdAndKindAndTargetId(project.getId(), kind, targetId)
                .orElseGet(
                        () ->
                                links.save(
                                        new PluginComponentLink(
                                                project.getId(), kind, targetId, actor.userId())));
    }

    @Transactional
    public void unlink(Long projectId, Long linkId, PluginActor actor) {
        lockMaintained(projectId, actor);
        var link =
                links.findById(linkId)
                        .orElseThrow(
                                () ->
                                        new DomainNotFoundException(
                                                "error.plugin.componentUnavailable"));
        if (!link.getProjectId().equals(projectId))
            throw new DomainNotFoundException("error.plugin.componentUnavailable");
        links.delete(link);
    }

    private void requireReason(String value) {
        if (value == null || value.isBlank() || value.length() > 10000)
            throw new DomainBadRequestException("error.plugin.governanceReason");
    }
}
