package com.iflytek.skillhub.domain.plugin.service;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.shared.exception.*;

import org.springframework.stereotype.Service;

import java.util.*;

/** Business boundary for a selected offline candidate; no archive, JSON or storage dependency. */
@Service
public class PluginOfflineImportWorkflow {
    private final NamespaceRepository namespaces;
    private final PluginProjectRepository projects;
    private final PluginReleaseRepository releases;
    private final PluginDistributionRepository distributions;
    private final PluginReleaseWorkflow workflow;

    public PluginOfflineImportWorkflow(
            NamespaceRepository namespaces,
            PluginProjectRepository projects,
            PluginReleaseRepository releases,
            PluginDistributionRepository distributions,
            PluginReleaseWorkflow workflow) {
        this.namespaces = namespaces;
        this.projects = projects;
        this.releases = releases;
        this.distributions = distributions;
        this.workflow = workflow;
    }

    public void requireImport(Long namespace, boolean repository, PluginActor actor) {
        if (actor.userId() == null
                || (repository
                        ? !(actor.reviewer() || actor.namespaceAdmin(namespace))
                        : !(actor.superAdmin() || actor.namespaceRoles().containsKey(namespace))))
            throw new DomainForbiddenException("error.plugin.permission");
        if (namespaces
                        .findById(namespace)
                        .orElseThrow(
                                () -> new DomainNotFoundException("error.plugin.namespaceNotFound"))
                        .getStatus()
                != NamespaceStatus.ACTIVE)
            throw new DomainBadRequestException("error.plugin.namespaceInactive");
    }

    public Result importCandidate(
            Long namespace,
            boolean repository,
            Long projectId,
            String slug,
            PluginVisibility visibility,
            String runtime,
            String name,
            String version,
            String sha,
            String objectKey,
            String inventory,
            String manifest,
            PluginActor actor) {
        requireImport(namespace, repository, actor);
        if (!PluginVersions.valid(version))
            throw new DomainBadRequestException("error.plugin.version");
        PluginProject project =
                projectId == null
                        ? projects.findByNamespaceIdAndSlug(namespace, slug).orElse(null)
                        : projects.findByIdForUpdate(projectId)
                                .orElseThrow(
                                        () -> new DomainNotFoundException("error.plugin.notFound"));
        if (project != null) {
            if (!project.getNamespaceId().equals(namespace))
                throw new DomainForbiddenException("error.plugin.permission");
            workflow.requireActive(project);
            if (!(repository && (actor.reviewer() || actor.namespaceAdmin(namespace))))
                workflow.requireWritable(project, actor);
            project = projects.findByIdForUpdate(project.getId()).orElseThrow();
        } else {
            SlugValidator.validate(slug);
            if (visibility == null) throw new DomainBadRequestException("error.plugin.metadata");
            project = new PluginProject(namespace, slug, name, actor.userId(), visibility);
            project.setCreatedBy(actor.userId());
            project.setUpdatedBy(actor.userId());
            project = projects.save(project);
        }
        PluginRelease release =
                releases.findByProjectIdAndVersion(project.getId(), version).orElse(null);
        if (release != null) {
            release = workflow.locked(release.getId());
            Optional<PluginDistribution> existing =
                    distributions.findByReleaseIdOrderByRuntimeKey(release.getId()).stream()
                            .filter(d -> d.getRuntimeKey().equals(runtime))
                            .findFirst();
            if (existing.isPresent())
                return new Result(
                        sha.equals(existing.get().getIntegrityDigest()) ? "IDENTICAL" : "CONFLICT",
                        project.getId(),
                        release.getId());
            release.requireDraft();
        } else {
            release = new PluginRelease(project.getId(), version, actor.userId());
            release = releases.save(release);
        }
        if (!Set.of("claude-code", "codex", "pi").contains(runtime))
            throw new DomainBadRequestException("error.plugin.archiveConflict");
        distributions.save(
                PluginDistribution.hostedArchive(
                        release.getId(),
                        runtime,
                        objectKey,
                        sha,
                        name,
                        version,
                        inventory,
                        manifest));
        return new Result("IMPORTED", project.getId(), release.getId());
    }

    public record Result(String status, Long projectId, Long releaseId) {}
}
