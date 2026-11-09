package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.plugin.PluginProject;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.software.SoftwareApplication;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.publication.PublicationContentView;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins independent content identities for the publisher's management list. EntityManager keeps
 * this cross-domain presentation query in the app layer rather than a shared content aggregate.
 */
@Repository
@Transactional(readOnly = true)
public class PublicationContentQueryRepository {
    private final EntityManager entities;
    private final com.iflytek.skillhub.domain.skill.SkillVersionRepository skillVersions;
    private final com.iflytek.skillhub.domain.plugin.PluginReleaseRepository pluginVersions;
    private final com.iflytek.skillhub.domain.software.SoftwareReleaseRepository softwareVersions;
    public PublicationContentQueryRepository(EntityManager entities,
            com.iflytek.skillhub.domain.skill.SkillVersionRepository skillVersions,
            com.iflytek.skillhub.domain.plugin.PluginReleaseRepository pluginVersions,
            com.iflytek.skillhub.domain.software.SoftwareReleaseRepository softwareVersions) {
        this.entities = entities; this.skillVersions = skillVersions; this.pluginVersions = pluginVersions; this.softwareVersions = softwareVersions;
    }
    public PageResponse<PublicationContentView> list(PluginActor actor, String type, int page) {
        int safePage = Math.max(0, Math.min(99, page));
        int limit = (safePage + 1) * 20;
        var items = new ArrayList<PublicationContentView>();
        long total = 0;
        if (type == null || type.equals("ALL") || type.equals("SKILL")) {
            String from = " from Skill s, Namespace n where n.id = s.namespaceId and s.ownerId = :owner";
            total += count(from, actor.userId());
            var rows = entities.createQuery("select s, n" + from + " order by s.updatedAt desc, s.id desc", Object[].class)
                    .setParameter("owner", actor.userId()).setMaxResults(limit).getResultList();
            for (var row : rows) {
                var skill = (Skill) row[0]; var namespace = (Namespace) row[1];
                items.add(new PublicationContentView("SKILL", skill.getId(), namespace.getSlug(), skill.getSlug(),
                        skill.getDisplayName(), skill.getVisibility().name(), skill.getStatus().name(),
                        skill.getLatestVersionId() == null ? null : skillVersions.findById(skill.getLatestVersionId()).map(v -> v.getVersion()).orElse(null), skill.getUpdatedAt()));
            }
        }
        if (type == null || type.equals("ALL") || type.equals("PLUGIN")) {
            String from = " from PluginProject p, Namespace n where n.id = p.namespaceId and p.ownerId = :owner"
                    + (actor.superAdmin() ? "" : " and exists (select m.id from NamespaceMember m where m.namespaceId = p.namespaceId and m.userId = :owner)");
            total += count(from, actor.userId());
            var rows = entities.createQuery("select p, n" + from + " order by p.updatedAt desc, p.id desc", Object[].class)
                    .setParameter("owner", actor.userId()).setMaxResults(limit).getResultList();
            for (var row : rows) {
                var plugin = (PluginProject) row[0]; var namespace = (Namespace) row[1];
                items.add(new PublicationContentView("PLUGIN", plugin.getId(), namespace.getSlug(), plugin.getSlug(),
                        plugin.getDisplayName(), plugin.getVisibility().name(), plugin.getStatus().name(),
                        pluginVersions.findByProjectIdOrderByCreatedAtDesc(plugin.getId()).stream().findFirst().map(v -> v.getVersion()).orElse(null), plugin.getUpdatedAt()));
            }
        }
        if ((actor.superAdmin() || actor.platformRoles().contains("SOFTWARE_ADMIN"))
                && (type == null || type.equals("ALL") || type.equals("SOFTWARE"))) {
            String from = " from SoftwareApplication a where a.createdBy = :owner";
            total += count(from, actor.userId());
            var rows = entities.createQuery("select a" + from + " order by a.updatedAt desc, a.id desc", SoftwareApplication.class)
                    .setParameter("owner", actor.userId()).setMaxResults(limit).getResultList();
            for (var app : rows) items.add(new PublicationContentView("SOFTWARE", app.getId(), null, app.getSlug(),
                    app.getName(), "AUTHENTICATED", app.getStatus().name(),
                    softwareVersions.findByApplicationIdOrderByIdDesc(app.getId()).stream().findFirst().map(v -> v.getDisplayVersion()).orElse(null), app.getUpdatedAt()));
        }
        items.sort(Comparator.comparing(PublicationContentView::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PublicationContentView::type).thenComparing(PublicationContentView::resourceId));
        int start = Math.min(safePage * 20, items.size());
        return new PageResponse<>(items.subList(start, Math.min(start + 20, items.size())), total, safePage, 20);
    }
    private long count(String from, String owner) {
        return entities.createQuery("select count(*)" + from, Long.class).setParameter("owner", owner).getSingleResult();
    }
    public PublicationContentView skillTarget(String owner, String namespace, String slug) {
        var rows = entities.createQuery("select s, n from Skill s, Namespace n where n.id = s.namespaceId"
                        + " and n.slug = :namespace and s.slug = :slug and s.ownerId = :owner", Object[].class)
                .setParameter("namespace", namespace).setParameter("slug", slug).setParameter("owner", owner)
                .setMaxResults(1).getResultList();
        if (rows.isEmpty()) return null;
        var skill = (Skill) rows.getFirst()[0];
        return new PublicationContentView("SKILL", skill.getId(), namespace, slug, skill.getDisplayName(),
                skill.getVisibility().name(), skill.getStatus().name(),
                skill.getLatestVersionId() == null ? null : skillVersions.findById(skill.getLatestVersionId()).map(v -> v.getVersion()).orElse(null), skill.getUpdatedAt());
    }
}
