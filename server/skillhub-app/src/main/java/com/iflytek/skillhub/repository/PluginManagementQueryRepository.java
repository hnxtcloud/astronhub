package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.plugin.service.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins plugin, namespace, releases and review/task evidence for management projections.
 * EntityManager is used here because this is a presentation join across independent aggregates.
 */
@Repository
@Transactional(readOnly = true)
public class PluginManagementQueryRepository {
    private final EntityManager entities;
    private final NamespaceRepository namespaces;
    private final PluginReleaseRepository releases;
    private final PluginDistributionRepository distributions;
    private final PluginReviewDecisionRepository reviews;
    private final PluginPublicationTaskRepository tasks;
    private final PluginReleaseWorkflow workflow;

    public PluginManagementQueryRepository(EntityManager entities, NamespaceRepository namespaces,
            PluginReleaseRepository releases, PluginDistributionRepository distributions,
            PluginReviewDecisionRepository reviews, PluginPublicationTaskRepository tasks, PluginReleaseWorkflow workflow) {
        this.entities = entities; this.namespaces = namespaces; this.releases = releases;
        this.distributions = distributions; this.reviews = reviews; this.tasks = tasks; this.workflow = workflow;
    }

    public PageResponse<PluginProjectView> list(PluginActor actor, int page, int size, boolean pendingReview) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new DomainBadRequestException("error.plugin.page");
        }
        if (pendingReview && !actor.reviewer()) { throw new DomainForbiddenException("error.plugin.permission"); }
        Set<Long> memberIds = actor.namespaceRoles().isEmpty() ? Set.of(-1L) : actor.namespaceRoles().keySet();
        Set<Long> adminIds = new HashSet<>();
        actor.namespaceRoles().forEach((id, role) -> { if (actor.namespaceAdmin(id)) adminIds.add(id); });
        if (adminIds.isEmpty()) adminIds.add(-1L);
        String from = " from PluginProject p where (:reviewer = true or p.namespaceId in :adminIds"
                + " or (p.ownerId = :userId and p.namespaceId in :memberIds))";
        if (pendingReview) {
            from += " and exists (select r.id from PluginRelease r where r.projectId = p.id"
                    + " and r.status = com.iflytek.skillhub.domain.plugin.PluginReleaseStatus.PENDING_REVIEW"
                    + " and not exists (select t.id from PluginPublicationTask t where t.releaseId = r.id))";
        }
        TypedQuery<PluginProject> query = entities.createQuery("select p" + from + " order by p.updatedAt desc, p.id desc", PluginProject.class);
        TypedQuery<Long> count = entities.createQuery("select count(p)" + from, Long.class);
        for (TypedQuery<?> q : List.of(query, count)) {
            q.setParameter("reviewer", actor.reviewer()).setParameter("adminIds", adminIds)
                    .setParameter("userId", actor.userId()).setParameter("memberIds", memberIds);
        }
        List<PluginProjectView> projects = query.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
                .map(p -> projectView(p, actor, false)).toList();
        return new PageResponse<>(projects, count.getSingleResult(), page, size);
    }

    public PluginProjectView detail(Long projectId, PluginActor actor) {
        PluginProject project = workflow.requireProject(projectId);
        if (!workflow.canManageRead(project, actor)) { throw new DomainForbiddenException("error.plugin.permission"); }
        return projectView(project, actor, true);
    }

    public PluginReleaseView release(Long releaseId, PluginActor actor) {
        PluginRelease release = releases.findById(releaseId)
                .orElseThrow(() -> new DomainNotFoundException("error.plugin.releaseNotFound"));
        if (!workflow.canManageRead(workflow.requireProject(release.getProjectId()), actor)) {
            throw new DomainForbiddenException("error.plugin.permission");
        }
        return releaseView(release);
    }

    private PluginProjectView projectView(PluginProject project, PluginActor actor, boolean includeReleases) {
        Namespace namespace = namespaces.findById(project.getNamespaceId()).orElseThrow();
        boolean canManage = actor.superAdmin() || actor.namespaceAdmin(project.getNamespaceId())
                || (Objects.equals(actor.userId(), project.getOwnerId()) && actor.namespaceRoles().containsKey(project.getNamespaceId()));
        return new PluginProjectView(project.getId(), project.getNamespaceId(), namespace.getSlug(), project.getSlug(),
                project.getDisplayName(), project.getSummary(), project.getOwnerId(), project.getVisibility(), project.getStatus(),
                project.isHidden(), project.getLatestReleaseId(), canManage && namespace.getStatus() == NamespaceStatus.ACTIVE
                && project.getStatus() == PluginProjectStatus.ACTIVE, actor.reviewer(), workflow.canManageRead(project, actor),
                includeReleases ? releases.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream().map(this::releaseView).toList() : List.of());
    }

    private PluginReleaseView releaseView(PluginRelease release) {
        List<PluginDistributionView> artifacts = distributions.findByReleaseIdOrderByRuntimeKey(release.getId()).stream()
                .map(d -> new PluginDistributionView(d.getId(), d.getRuntimeKey(), d.getNativeName(), d.getNativeVersion(),
                        d.getIntegrityDigest(), d.getCompatibilityEvidence(), d.getSecurityAssessment(), d.getInventoryJson(), d.getManifestJson())).toList();
        List<PluginReviewView> history = reviews.findByReleaseIdOrderByCreatedAtDesc(release.getId()).stream()
                .map(r -> new PluginReviewView(r.getReviewerId(), r.getDecision(), r.getNotes(), r.getRuntimeTestResult(),
                        r.getTestEnvironment(), r.getCreatedAt())).toList();
        PluginPublicationView publication = tasks.findByReleaseId(release.getId())
                .map(t -> new PluginPublicationView(t.getId(), t.getStatus(), t.getRequiredChannels(), t.getCompletedChannels(),
                        t.getLastError(), t.getAttemptCount(), t.getCompletedAt())).orElse(null);
        return new PluginReleaseView(release.getId(), release.getProjectId(), release.getVersion(), release.getStatus(),
                release.getChangelog(), release.getCreatedAt(), release.getPublishedAt(), artifacts, history, publication, release.getYankedAt(), release.getYankedBy(), release.getYankReason());
    }
}
