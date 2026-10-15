package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.plugin.service.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.dto.plugin.PluginAccessDtos.*;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.PluginDistributionView;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Published historical read projections; never returns source keys or nonpublished versions. */
@Repository
@Transactional(readOnly = true)
public class PluginPublishedQueryRepository {
    private final NamespaceRepository namespaces;
    private final PluginProjectRepository projects;
    private final PluginReleaseRepository releases;
    private final PluginDistributionRepository distributions;
    private final PluginReleaseWorkflow workflow;
    private final PluginComponentQueryRepository components;

    public PluginPublishedQueryRepository(
            NamespaceRepository namespaces,
            PluginProjectRepository projects,
            PluginReleaseRepository releases,
            PluginDistributionRepository distributions,
            PluginReleaseWorkflow workflow,
            PluginComponentQueryRepository components) {
        this.namespaces = namespaces;
        this.projects = projects;
        this.releases = releases;
        this.distributions = distributions;
        this.workflow = workflow;
        this.components = components;
    }

    public String detailNamespace(Long namespaceId) {
        return namespaces
                .findById(namespaceId)
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.namespaceNotFound"))
                .getSlug();
    }

    public PluginProject project(String namespace, String slug, PluginActor actor) {
        var ns =
                namespaces
                        .findBySlug(namespace)
                        .orElseThrow(() -> new DomainNotFoundException("error.plugin.notFound"));
        var project =
                projects.findByNamespaceIdAndSlug(ns.getId(), slug)
                        .orElseThrow(() -> new DomainNotFoundException("error.plugin.notFound"));
        if (!workflow.canReadPublished(project, actor))
            throw new DomainNotFoundException("error.plugin.notFound");
        return project;
    }

    public PluginRelease release(String namespace, String slug, String version, PluginActor actor) {
        var project = project(namespace, slug, actor);
        return releases.findByProjectIdAndVersion(project.getId(), version)
                .filter(r -> r.getStatus() == PluginReleaseStatus.PUBLISHED)
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.releaseNotFound"));
    }

    public PublishedPluginView detail(String namespace, String slug, PluginActor actor) {
        var project = project(namespace, slug, actor);
        var versions =
                releases.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                        .filter(r -> r.getStatus() == PluginReleaseStatus.PUBLISHED)
                        .map(
                                r ->
                                        new PublishedPluginVersion(
                                                r.getId(),
                                                r.getVersion(),
                                                r.getPublishedAt(),
                                                distributions
                                                        .findByReleaseIdOrderByRuntimeKey(r.getId())
                                                        .stream()
                                                        .map(PluginDistribution::getRuntimeKey)
                                                        .toList()))
                        .toList();
        if (versions.isEmpty()) throw new DomainNotFoundException("error.plugin.notFound");
        return new PublishedPluginView(
                project.getId(),
                namespace,
                slug,
                project.getDisplayName(),
                project.getSummary(),
                project.getVisibility(),
                project.getLatestReleaseId(),
                versions,
                components.list(project.getId(), actor));
    }

    public PublishedPluginReleaseView detail(
            String namespace, String slug, String version, PluginActor actor) {
        var r = release(namespace, slug, version, actor);
        return new PublishedPluginReleaseView(
                r.getId(),
                r.getProjectId(),
                r.getVersion(),
                r.getChangelog(),
                r.getPublishedAt(),
                distributions.findByReleaseIdOrderByRuntimeKey(r.getId()).stream()
                        .map(
                                d ->
                                        new PluginDistributionView(
                                                d.getId(),
                                                d.getRuntimeKey(),
                                                d.getNativeName(),
                                                d.getNativeVersion(),
                                                d.getIntegrityDigest(),
                                                d.getCompatibilityEvidence(),
                                                d.getSecurityAssessment(),
                                                d.getInventoryJson(),
                                                d.getManifestJson()))
                        .toList());
    }
}
