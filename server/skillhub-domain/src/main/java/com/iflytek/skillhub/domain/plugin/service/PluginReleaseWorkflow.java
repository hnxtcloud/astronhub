package com.iflytek.skillhub.domain.plugin.service;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PluginReleaseWorkflow {
    private final NamespaceRepository namespaces;
    private final PluginProjectRepository projects;
    private final PluginReleaseRepository releases;
    private final PluginDistributionRepository distributions;
    private final PluginReviewDecisionRepository reviews;
    private final PluginPublicationTaskRepository tasks;

    public PluginReleaseWorkflow(NamespaceRepository namespaces, PluginProjectRepository projects,
            PluginReleaseRepository releases, PluginDistributionRepository distributions,
            PluginReviewDecisionRepository reviews, PluginPublicationTaskRepository tasks) {
        this.namespaces = namespaces; this.projects = projects; this.releases = releases;
        this.distributions = distributions; this.reviews = reviews; this.tasks = tasks;
    }

    public PluginProject requireProject(Long projectId) {
        return projects.findById(projectId).orElseThrow(() -> new DomainNotFoundException("error.plugin.notFound"));
    }

    @Transactional
    public PluginProject updateProject(Long projectId, String displayName, String summary, PluginActor actor) {
        PluginProject project = projects.findByIdForUpdate(projectId)
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.notFound"));
        requireWritable(project, actor);
        project.updateProfile(displayName, summary, actor.userId());
        return projects.save(project);
    }

    @Transactional
    public PluginRelease createRelease(Long projectId, String version, String changelog, PluginActor actor) {
        PluginProject project = projects.findByIdForUpdate(projectId)
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.notFound"));
        requireWritable(project, actor);
        if (!PluginVersions.valid(version)
                || (changelog != null && changelog.length() > 30000)) {
            throw new DomainBadRequestException("error.plugin.version");
        }
        if (releases.findByProjectIdAndVersion(projectId, version).isPresent()) {
            throw new DomainBadRequestException("error.plugin.versionExists");
        }
        PluginRelease release = new PluginRelease(projectId, version, actor.userId());
        release.setChangelog(changelog);
        return releases.save(release);
    }

    @Transactional
    public PluginRelease updateDraft(Long releaseId, String changelog, PluginActor actor) {
        PluginRelease release = locked(releaseId);
        requireWritable(requireProject(release.getProjectId()), actor);
        if (changelog != null && changelog.length() > 30000) { throw new DomainBadRequestException("error.plugin.metadata"); }
        release.setChangelog(changelog);
        return releases.save(release);
    }

    @Transactional
    public PluginDistribution attachArchive(Long releaseId, String objectKey, String sha256, String nativeName,
            String nativeVersion, String inventoryJson, String manifestJson, PluginActor actor) {
        return attachArchive(releaseId, "claude-code", objectKey, sha256, nativeName, nativeVersion, inventoryJson, manifestJson, actor);
    }

    @Transactional
    public PluginDistribution attachArchive(Long releaseId, String runtime, String objectKey, String sha256, String nativeName,
            String nativeVersion, String inventoryJson, String manifestJson, PluginActor actor) {
        PluginRelease release = locked(releaseId);
        requireWritable(requireProject(release.getProjectId()), actor);
        release.requireDraft();
        if (!Set.of("claude-code", "codex", "pi").contains(runtime) || !release.getVersion().equals(nativeVersion)) {
            throw new DomainBadRequestException("error.plugin.archiveConflict");
        }
        List<PluginDistribution> existing = distributions.findByReleaseIdOrderByRuntimeKey(releaseId);
        if (existing.stream().anyMatch(d -> runtime.equals(d.getRuntimeKey()))) {
            throw new DomainBadRequestException("error.plugin.archiveConflict");
        }
        return distributions.save(PluginDistribution.hostedArchive(releaseId, runtime, objectKey,
                sha256, nativeName, nativeVersion, inventoryJson, manifestJson));
    }

    public boolean canManageRead(PluginProject project, PluginActor actor) {
        return actor.userId() != null && (actor.reviewer() || actor.namespaceAdmin(project.getNamespaceId())
                || (actor.userId().equals(project.getOwnerId()) && actor.namespaceRoles().containsKey(project.getNamespaceId())));
    }

    public void requireDownload(PluginRelease release, PluginActor actor, boolean inspection) {
        PluginProject project = requireProject(release.getProjectId());
        if (inspection) {
            if (!canManageRead(project, actor)) { throw new DomainForbiddenException("error.plugin.permission"); }
            return;
        }
        if (release.getStatus() != PluginReleaseStatus.PUBLISHED || project.isHidden()
                || project.getStatus() != PluginProjectStatus.ACTIVE) {
            throw new DomainNotFoundException("error.plugin.releaseNotFound");
        }
        if (actor.userId() == null || !(actor.reviewer() || (actor.userId().equals(project.getOwnerId()) && actor.namespaceRoles().containsKey(project.getNamespaceId()))
                || actor.namespaceAdmin(project.getNamespaceId()) || project.getVisibility() == PluginVisibility.PUBLIC
                || (project.getVisibility() == PluginVisibility.NAMESPACE_ONLY && actor.namespaceRoles().containsKey(project.getNamespaceId())))) {
            throw new DomainForbiddenException("error.plugin.permission");
        }
    }

    /** Public metadata and archive readers share visibility; ownership never survives membership revocation. */
    public boolean canReadPublished(PluginProject project, PluginActor actor) {
        return project.getStatus() == PluginProjectStatus.ACTIVE && !project.isHidden()
                && (project.getVisibility() == PluginVisibility.PUBLIC || actor.reviewer()
                || actor.namespaceAdmin(project.getNamespaceId())
                || (actor.userId() != null && actor.userId().equals(project.getOwnerId())
                    && actor.namespaceRoles().containsKey(project.getNamespaceId()))
                || (project.getVisibility() == PluginVisibility.NAMESPACE_ONLY
                    && actor.namespaceRoles().containsKey(project.getNamespaceId())));
    }

    /** Durable approval order is user publication intent; never rank network completion timestamps. */
    public void refreshPublishedSelection(PluginProject project) {
        var candidates = releases.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .filter(r -> r.getStatus() == PluginReleaseStatus.PUBLISHED).toList();
        var selected = candidates.stream().max(java.util.Comparator
                .comparingLong((PluginRelease r) -> tasks.findByReleaseId(r.getId())
                        .map(PluginPublicationTask::getId).orElse(-1L))
                .thenComparing(PluginRelease::getId));
        if (selected.isPresent()) project.pointToPublishedRelease(selected.get());
        else project.clearPublishedRelease();
        projects.save(project);
    }

    @Transactional
    public PluginRelease completeHosted(Long releaseId) {
        PluginRelease release = locked(releaseId);
        PluginProject project = projects.findByIdForUpdate(release.getProjectId())
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.notFound"));
        requireActive(project);
        PluginPublicationTask task = tasks.findByReleaseId(releaseId)
                .orElseThrow(() -> new DomainBadRequestException("error.plugin.reviewState"));
        if ("SUCCEEDED".equals(task.getStatus())) { return release; }
        if (release.getStatus() != PluginReleaseStatus.PENDING_REVIEW) {
            throw new DomainBadRequestException("error.plugin.reviewState");
        }
        task.completeHosted(); tasks.save(task);
        if ("SUCCEEDED".equals(task.getStatus())) {
            release.publish(); releases.save(release);
            refreshPublishedSelection(project);
        }
        return release;
    }

    public void requireWritable(PluginProject project, PluginActor actor) {
        requireActive(project);
        if (actor.userId() == null || (!actor.superAdmin() && !actor.namespaceAdmin(project.getNamespaceId())
                && !(actor.userId().equals(project.getOwnerId()) && actor.namespaceRoles().containsKey(project.getNamespaceId())))) {
            throw new DomainForbiddenException("error.plugin.permission");
        }
    }

    public void requireActive(PluginProject project) {
        Namespace namespace = namespaces.findById(project.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.namespaceNotFound"));
        if (namespace.getStatus() != NamespaceStatus.ACTIVE || project.getStatus() != PluginProjectStatus.ACTIVE) {
            throw new DomainBadRequestException("error.plugin.namespaceInactive");
        }
    }

    @Transactional
    public PluginRelease submit(Long releaseId, PluginActor actor) {
        PluginRelease release = locked(releaseId);
        requireWritable(requireProject(release.getProjectId()), actor);
        release.requireDraft();
        requireValidated(release);
        release.submit();
        return releases.save(release);
    }

    @Transactional
    public PluginPublicationTask approve(Long releaseId, String notes, String testResult, String environment,
                                          PluginActor actor, boolean direct) {
        if (!actor.reviewer() || (direct && !actor.superAdmin())) {
            throw new DomainForbiddenException("error.plugin.permission");
        }
        PluginRelease release = locked(releaseId);
        PluginProject project = projects.findByIdForUpdate(release.getProjectId())
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.notFound"));
        requireActive(project);
        requireValidated(release);
        if (tasks.findByReleaseId(releaseId).isPresent()) {
            throw new DomainBadRequestException("error.plugin.alreadyApproved");
        }
        if (direct) { release.submit(); releases.save(release); }
        if (release.getStatus() != PluginReleaseStatus.PENDING_REVIEW) {
            throw new DomainBadRequestException("error.plugin.reviewState");
        }
        saveReview(releaseId, actor, direct ? "DIRECT_APPROVED" : "APPROVED", notes, testResult, environment);
        List<PluginDistribution> archives = distributions.findByReleaseIdOrderByRuntimeKey(releaseId);
        Set<String> channels = new TreeSet<>();
        channels.add("HOSTED");
        if (project.getVisibility() == PluginVisibility.PUBLIC) {
            for (PluginDistribution archive : archives) {
                channels.add("pi".equals(archive.getRuntimeKey()) ? "NPM" : "GIT");
            }
        }
        String snapshot = archives.stream().map(a -> a.getRuntimeKey() + ":" + a.getIntegrityDigest())
                .sorted().collect(Collectors.joining("\n"));
        return tasks.save(new PluginPublicationTask(releaseId, actor.userId(), String.join(",", channels),
                project.getVisibility().name() + "\n" + release.getVersion() + "\n" + snapshot));
    }

    @Transactional
    public PluginReviewDecision reject(Long releaseId, String notes, String testResult, String environment, PluginActor actor) {
        if (!actor.reviewer()) { throw new DomainForbiddenException("error.plugin.permission"); }
        PluginRelease release = locked(releaseId);
        requireActive(requireProject(release.getProjectId()));
        if (release.getStatus() != PluginReleaseStatus.PENDING_REVIEW || tasks.findByReleaseId(releaseId).isPresent()) {
            throw new DomainBadRequestException("error.plugin.reviewState");
        }
        PluginReviewDecision decision = saveReview(releaseId, actor, "REJECTED", notes, testResult, environment);
        release.reject(); releases.save(release);
        return decision;
    }

    private PluginReviewDecision saveReview(Long releaseId, PluginActor actor, String decision, String notes,
                                            String testResult, String environment) {
        String result = testResult == null ? "UNKNOWN" : testResult;
        if (notes == null || notes.isBlank() || notes.length() > 10000
                || !Set.of("UNKNOWN", "PASSED", "FAILED").contains(result)
                || (!"UNKNOWN".equals(result) && (environment == null || environment.isBlank()))
                || (environment != null && environment.length() > 10000)) {
            throw new DomainBadRequestException("error.plugin.reviewEvidence");
        }
        return reviews.save(new PluginReviewDecision(releaseId, actor.userId(), decision, notes, result, environment));
    }

    public PluginRelease readRelease(Long releaseId) { return releases.findById(releaseId).orElseThrow(() -> new DomainNotFoundException("error.plugin.releaseNotFound")); }

    public PluginRelease locked(Long releaseId) {
        return releases.findByIdForUpdate(releaseId)
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.releaseNotFound"));
    }

    private void requireValidated(PluginRelease release) {
        List<PluginDistribution> archives = distributions.findByReleaseIdOrderByRuntimeKey(release.getId());
        if (archives.isEmpty() || archives.stream().anyMatch(d ->
                d.getCompatibilityEvidence() != PluginCompatibilityEvidence.MANIFEST_VALIDATED
                || d.getIntegrityDigest() == null || !d.getIntegrityDigest().matches("[a-f0-9]{64}")
                || !"HOSTED_ARCHIVE".equals(d.getSourceType()))) {
            throw new DomainBadRequestException("error.plugin.validationRequired");
        }
    }
}
