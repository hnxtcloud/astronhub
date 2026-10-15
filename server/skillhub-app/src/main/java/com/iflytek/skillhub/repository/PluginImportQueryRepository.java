package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.plugin.service.*;
import com.iflytek.skillhub.dto.plugin.PluginImportDtos.*;

import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Joins uploaded candidate evidence with namespace project/release/distribution identity for
 * preview.
 */
@Repository
public class PluginImportQueryRepository {
    private final PluginProjectRepository projects;
    private final PluginReleaseRepository releases;
    private final PluginDistributionRepository distributions;
    private final PluginReleaseWorkflow workflow;

    public PluginImportQueryRepository(
            PluginProjectRepository projects,
            PluginReleaseRepository releases,
            PluginDistributionRepository distributions,
            PluginReleaseWorkflow workflow) {
        this.projects = projects;
        this.releases = releases;
        this.distributions = distributions;
        this.workflow = workflow;
    }

    public ImportPreviewView preview(
            PluginImportBatch batch, List<StoredCandidate> candidates, PluginActor actor) {
        List<ImportCandidateView> views =
                candidates.stream()
                        .map(
                                c ->
                                        new ImportCandidateView(
                                                c.id(),
                                                c.directory(),
                                                c.runtime(),
                                                c.nativeName(),
                                                c.version(),
                                                c.suggestedSlug(),
                                                c.sha256(),
                                                c.format(),
                                                c.inventoryJson(),
                                                c.manifestJson(),
                                                classification(
                                                        batch.getNamespaceId(),
                                                        c,
                                                        actor,
                                                        batch.isRepository()),
                                                c.error()))
                        .toList();
        return new ImportPreviewView(
                batch.getId(),
                batch.getNamespaceId(),
                batch.isRepository(),
                batch.getExpiresAt(),
                views);
    }

    private String classification(
            Long namespace, StoredCandidate candidate, PluginActor actor, boolean repository) {
        if (candidate.error() != null) return "INVALID";
        var project = projects.findByNamespaceIdAndSlug(namespace, candidate.suggestedSlug());
        if (project.isEmpty()) return "NEW_PLUGIN";
        if (!workflow.canManageRead(project.get(), actor)
                && !(repository && (actor.reviewer() || actor.namespaceAdmin(namespace))))
            return "TARGET_UNAVAILABLE";
        var release =
                releases.findByProjectIdAndVersion(project.get().getId(), candidate.version());
        if (release.isEmpty()) return "NEW_VERSION";
        var distribution =
                distributions.findByReleaseIdOrderByRuntimeKey(release.get().getId()).stream()
                        .filter(d -> d.getRuntimeKey().equals(candidate.runtime()))
                        .findFirst();
        return distribution
                .map(
                        d ->
                                d.getIntegrityDigest().equals(candidate.sha256())
                                        ? "IDENTICAL"
                                        : "CONFLICT")
                .orElse(
                        release.get().getStatus() == PluginReleaseStatus.DRAFT
                                ? "NEW_RUNTIME"
                                : "CONFLICT");
    }

    public record StoredCandidate(
            String id,
            String directory,
            String runtime,
            String nativeName,
            String version,
            String suggestedSlug,
            String sha256,
            String format,
            String inventoryJson,
            String manifestJson,
            String error,
            String objectKey) {}
}
