package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.mcp.*;
import com.iflytek.skillhub.domain.mcp.service.McpActor;
import com.iflytek.skillhub.domain.mcp.service.McpCatalogWorkflow;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins MCP entries, namespaces, revisions, profiles and review decisions for management
 * projections. EntityManager is used here because this is a presentation join across independent
 * aggregates.
 */
@Repository
@Transactional(readOnly = true)
public class McpManagementQueryRepository {
    private final EntityManager entities;
    private final NamespaceRepository namespaces;
    private final McpEntryRevisionRepository revisions;
    private final McpConnectionProfileRepository profiles;
    private final McpReviewDecisionRepository decisions;
    private final McpCatalogWorkflow workflow;
    private final ObjectMapper mapper;

    public McpManagementQueryRepository(EntityManager entities, NamespaceRepository namespaces,
            McpEntryRevisionRepository revisions, McpConnectionProfileRepository profiles,
            McpReviewDecisionRepository decisions, McpCatalogWorkflow workflow, ObjectMapper mapper) {
        this.entities = entities;
        this.namespaces = namespaces;
        this.revisions = revisions;
        this.profiles = profiles;
        this.decisions = decisions;
        this.workflow = workflow;
        this.mapper = mapper;
    }

    public PageResponse<McpEntryView> list(McpActor actor, int page, int size, boolean pendingReview) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new DomainBadRequestException("error.mcp.page");
        }
        boolean namespaceReviewer = actor.reviewer()
                || actor.namespaceRoles().keySet().stream().anyMatch(actor::namespaceAdmin);
        if (pendingReview && !namespaceReviewer) {
            throw new DomainForbiddenException("error.mcp.permission");
        }
        Set<Long> memberIds = actor.namespaceRoles().isEmpty() ? Set.of(-1L) : actor.namespaceRoles().keySet();
        Set<Long> adminIds = new HashSet<>();
        actor.namespaceRoles().forEach((id, role) -> {
            if (actor.namespaceAdmin(id)) adminIds.add(id);
        });
        if (adminIds.isEmpty()) adminIds.add(-1L);
        String from = " from McpServerEntry e where (:reviewer = true or e.namespaceId in :adminIds"
                + " or (e.ownerId = :userId and e.namespaceId in :memberIds))";
        if (pendingReview) {
            from += " and exists (select r.id from McpEntryRevision r where r.entryId = e.id"
                    + " and r.status = com.iflytek.skillhub.domain.mcp.McpRevisionStatus.PENDING_REVIEW)";
        }
        TypedQuery<McpServerEntry> query = entities.createQuery(
                "select e" + from + " order by e.updatedAt desc, e.id desc", McpServerEntry.class);
        TypedQuery<Long> count = entities.createQuery("select count(e)" + from, Long.class);
        for (TypedQuery<?> q : List.of(query, count)) {
            q.setParameter("reviewer", namespaceReviewer)
                    .setParameter("adminIds", adminIds)
                    .setParameter("userId", actor.userId())
                    .setParameter("memberIds", memberIds);
        }
        List<McpEntryView> entries = query.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
                .map(entry -> entryView(entry, actor, false))
                .toList();
        return new PageResponse<>(entries, count.getSingleResult(), page, size);
    }

    public McpEntryView detail(Long entryId, McpActor actor) {
        McpServerEntry entry = workflow.requireEntry(entryId);
        requireReadAccess(entry, actor);
        return entryView(entry, actor, true);
    }

    public McpRevisionView revision(Long revisionId, McpActor actor) {
        McpEntryRevision revision = revisions.findById(revisionId)
                .orElseThrow(() -> new DomainNotFoundException("error.mcp.revisionNotFound"));
        requireReadAccess(workflow.requireEntry(revision.getEntryId()), actor);
        return revisionView(revision);
    }

    private void requireReadAccess(McpServerEntry entry, McpActor actor) {
        if (!actor.canMaintain(entry) && !actor.canReview(entry)) {
            throw new DomainForbiddenException("error.mcp.permission");
        }
    }

    private McpEntryView entryView(McpServerEntry entry, McpActor actor, boolean includeRevisions) {
        Namespace namespace = namespaces.findById(entry.getNamespaceId()).orElseThrow();
        boolean maintainable = actor.canMaintain(entry);
        List<McpEntryRevision> history = revisions.findByEntryIdOrderByRevisionNumberDesc(entry.getId());
        boolean pendingReview = history.stream()
                .anyMatch(revision -> revision.getStatus() == McpRevisionStatus.PENDING_REVIEW);
        return new McpEntryView(entry.getId(), entry.getNamespaceId(), namespace.getSlug(), entry.getSlug(),
                entry.getDisplayName(), entry.getSummary(), entry.getOwnerId(), entry.getVisibility(),
                entry.getStatus(), entry.isHidden(), entry.getLatestRevisionId(),
                maintainable && namespace.getStatus() == NamespaceStatus.ACTIVE
                        && entry.getStatus() == McpEntryStatus.ACTIVE,
                actor.canReview(entry), maintainable, pendingReview,
                includeRevisions ? history.stream().map(this::revisionView).toList() : List.of());
    }

    private McpRevisionView revisionView(McpEntryRevision revision) {
        List<McpProfileView> profileViews = profiles.findByRevisionIdOrderByProfileKey(revision.getId()).stream()
                .map(this::profileView)
                .toList();
        List<McpReviewDecisionView> reviewViews = decisions.findByRevisionIdOrderByCreatedAtDesc(revision.getId())
                .stream()
                .map(decision -> new McpReviewDecisionView(
                        decision.getReviewerId(), decision.getDecision(), decision.getNotes(),
                        decision.getCreatedAt()))
                .toList();
        return new McpRevisionView(revision.getId(), revision.getRevisionNumber(), revision.getStatus().name(),
                revision.getDescription(), revision.getSourceKind(), revision.getLocalCuration(),
                revision.getCreatedAt(), revision.getPublishedAt(), profileViews, reviewViews);
    }

    private McpProfileView profileView(McpConnectionProfile profile) {
        return new McpProfileView(profile.getProfileKey(), profile.getDisplayName(), profile.getTransportType(),
                profile.getEndpointTemplate(), profile.getCommandTemplate(),
                parseArguments(profile.getArgumentsJson()), parseVariables(profile.getVariablesJson()));
    }

    /** Malformed stored templates stay visible as empty structured input instead of failing the page. */
    private List<String> parseArguments(String argumentsJson) {
        JsonNode node = parseArray(argumentsJson);
        List<String> arguments = new java.util.ArrayList<>();
        if (node == null) return arguments;
        for (JsonNode argument : node) {
            if (argument.isTextual()) arguments.add(argument.asText());
        }
        return arguments;
    }

    private List<McpVariableView> parseVariables(String variablesJson) {
        JsonNode node = parseArray(variablesJson);
        List<McpVariableView> variables = new java.util.ArrayList<>();
        if (node == null) return variables;
        for (JsonNode variable : node) {
            String name = variable.path("name").asText(null);
            if (name == null) continue;
            JsonNode defaultValue = variable.has("defaultValue") ? variable.get("defaultValue") : variable.get("default");
            variables.add(new McpVariableView(
                    name,
                    variable.path("description").isTextual() ? variable.path("description").asText() : null,
                    variable.path("required").asBoolean(false),
                    variable.path("secret").asBoolean(false),
                    defaultValue == null || defaultValue.isNull() ? null : defaultValue.asText(),
                    variable.path("validationHint").isTextual() ? variable.path("validationHint").asText() : null));
        }
        return variables;
    }

    private JsonNode parseArray(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            JsonNode parsed = mapper.readTree(value);
            return parsed != null && parsed.isArray() ? parsed : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return null;
        }
    }
}
