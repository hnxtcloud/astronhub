package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.mcp.*;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.dto.plugin.PluginAccessDtos.*;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Non-owning projections always re-evaluate the target's independent live access policy. */
@Repository
@Transactional(readOnly = true)
public class PluginComponentQueryRepository {
    private final PluginComponentLinkRepository links;
    private final NamespaceRepository namespaces;
    private final SkillRepository skills;
    private final SkillVersionRepository versions;
    private final SkillQueryService skillQueries;
    private final McpServerEntryRepository mcps;
    private final McpEntryRevisionRepository revisions;
    private final CapabilityCatalogQueryRepository catalog;

    public PluginComponentQueryRepository(
            PluginComponentLinkRepository links,
            NamespaceRepository namespaces,
            SkillRepository skills,
            SkillVersionRepository versions,
            SkillQueryService skillQueries,
            McpServerEntryRepository mcps,
            McpEntryRevisionRepository revisions,
            CapabilityCatalogQueryRepository catalog) {
        this.links = links;
        this.namespaces = namespaces;
        this.skills = skills;
        this.versions = versions;
        this.skillQueries = skillQueries;
        this.mcps = mcps;
        this.revisions = revisions;
        this.catalog = catalog;
    }

    public List<PluginLinkedComponentView> list(Long projectId, PluginActor actor) {
        return links.findByProjectIdOrderByIdAsc(projectId).stream()
                .map(link -> read(link, actor))
                .flatMap(Optional::stream)
                .toList();
    }

    public Long resolve(PluginComponentLinkRequest request, PluginActor actor) {
        var ns = namespaces.findBySlug(request.namespace()).orElseThrow(this::unavailable);
        if ("SKILL".equals(request.kind())) {
            var targets =
                    skills.findByNamespaceIdAndSlug(ns.getId(), request.slug()).stream()
                            .flatMap(
                                    skill ->
                                            versions
                                                    .findBySkillIdAndVersion(
                                                            skill.getId(), request.version())
                                                    .stream())
                            .filter(
                                    version ->
                                            read(
                                                            new PluginComponentLink(
                                                                    0L,
                                                                    "SKILL",
                                                                    version.getId(),
                                                                    actor.userId()),
                                                            actor)
                                                    .isPresent())
                            .toList();
            if (targets.size() != 1) throw unavailable();
            return targets.getFirst().getId();
        }
        if ("MCP".equals(request.kind())) {
            var entry =
                    mcps.findByNamespaceIdAndSlug(ns.getId(), request.slug())
                            .orElseThrow(this::unavailable);
            return revisions.findByEntryIdOrderByRevisionNumberDesc(entry.getId()).stream()
                    .filter(
                            revision ->
                                    Integer.toString(revision.getRevisionNumber())
                                            .equals(request.version()))
                    .filter(
                            revision ->
                                    read(
                                                    new PluginComponentLink(
                                                            0L,
                                                            "MCP",
                                                            revision.getId(),
                                                            actor.userId()),
                                                    actor)
                                            .isPresent())
                    .map(McpEntryRevision::getId)
                    .findFirst()
                    .orElseThrow(this::unavailable);
        }
        throw unavailable();
    }

    private Optional<PluginLinkedComponentView> read(PluginComponentLink link, PluginActor actor) {
        try {
            if ("SKILL".equals(link.getKind())) {
                var resolved =
                        skillQueries.resolveVersionById(
                                link.getTargetId(),
                                actor.userId(),
                                actor.namespaceRoles(),
                                actor.platformRoles());
                var skill = skills.findById(resolved.skillId()).orElseThrow(this::unavailable);
                return Optional.of(
                        new PluginLinkedComponentView(
                                link.getId(),
                                "SKILL",
                                resolved.namespace(),
                                resolved.slug(),
                                resolved.version(),
                                skill.getDisplayName() == null
                                        ? skill.getSlug()
                                        : skill.getDisplayName()));
            }
            if ("MCP".equals(link.getKind())) {
                var revision =
                        revisions
                                .findById(link.getTargetId())
                                .filter(r -> r.getStatus() == McpRevisionStatus.PUBLISHED)
                                .orElseThrow(this::unavailable);
                var entry = mcps.findById(revision.getEntryId()).orElseThrow(this::unavailable);
                var ns = namespaces.findById(entry.getNamespaceId()).orElseThrow(this::unavailable);
                if (catalog.findMcpServer(
                                ns.getSlug(),
                                entry.getSlug(),
                                actor.userId(),
                                actor.namespaceRoles(),
                                actor.platformRoles())
                        .isEmpty()) return Optional.empty();
                return Optional.of(
                        new PluginLinkedComponentView(
                                link.getId(),
                                "MCP",
                                ns.getSlug(),
                                entry.getSlug(),
                                Integer.toString(revision.getRevisionNumber()),
                                entry.getDisplayName()));
            }
            return Optional.empty();
        } catch (LocalizedDomainException ex) {
            return Optional.empty();
        }
    }

    private DomainNotFoundException unavailable() {
        return new DomainNotFoundException("error.plugin.componentUnavailable");
    }
}
