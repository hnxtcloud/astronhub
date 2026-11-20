package com.iflytek.skillhub.domain.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.mcp.*;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.SlugValidator;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Publication, review, and governance state machine for the MCP catalog. Published revisions are
 * immutable; editing a published entry creates a new draft while the published revision stays
 * visible (docs/25 "MCP Catalog Domain"). No gateway, runtime, or credential state lives here.
 */
@Service
public class McpCatalogWorkflow {
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_SERVER_JSON = "SERVER_JSON";
    private static final Set<String> SOURCE_KINDS = Set.of(SOURCE_MANUAL, SOURCE_SERVER_JSON);
    private static final int MAX_TEMPLATE_LENGTH = 2000;
    private static final int MAX_METADATA_LENGTH = 30000;
    private static final int MAX_SNAPSHOT_LENGTH = 200000;

    private final McpServerEntryRepository entries;
    private final McpEntryRevisionRepository revisions;
    private final McpConnectionProfileRepository profiles;
    private final McpReviewDecisionRepository decisions;
    private final NamespaceRepository namespaces;
    private final ObjectMapper objectMapper;

    public McpCatalogWorkflow(
            McpServerEntryRepository entries,
            McpEntryRevisionRepository revisions,
            McpConnectionProfileRepository profiles,
            McpReviewDecisionRepository decisions,
            NamespaceRepository namespaces,
            ObjectMapper objectMapper) {
        this.entries = entries;
        this.revisions = revisions;
        this.profiles = profiles;
        this.decisions = decisions;
        this.namespaces = namespaces;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- entry creation

    @Transactional
    public McpServerEntry createEntry(Long namespaceId, String slug, String displayName, String summary,
                                      McpVisibility visibility, List<McpProfileTemplate> templates,
                                      String sourceKind, String sourceSnapshot, McpActor actor) {
        Namespace namespace = namespaces.findById(namespaceId)
                .orElseThrow(() -> new DomainNotFoundException("error.mcp.namespaceNotFound"));
        if (actor.userId() == null || (!actor.superAdmin() && !actor.namespaceMember(namespaceId))) {
            throw new DomainForbiddenException("error.mcp.permission");
        }
        if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
            throw new DomainBadRequestException("error.mcp.namespaceInactive");
        }
        SlugValidator.validate(slug);
        if (displayName == null || displayName.isBlank() || displayName.length() > 200 || visibility == null) {
            throw new DomainBadRequestException("error.mcp.metadata");
        }
        requireMetadataLength(summary);
        requireSnapshotLength(sourceSnapshot);
        requireSourceKind(sourceKind);
        if (entries.findByNamespaceIdAndSlug(namespaceId, slug).isPresent()) {
            throw new DomainConflictException("error.mcp.exists");
        }
        validateTemplates(templates);
        McpServerEntry entry = new McpServerEntry(namespaceId, slug, displayName.trim(), actor.userId(), visibility);
        entry.setSummary(summary);
        entry.setCreatedBy(actor.userId());
        entry.setUpdatedBy(actor.userId());
        McpServerEntry saved = entries.save(entry);
        McpEntryRevision revision = new McpEntryRevision(saved.getId(), 1, sourceKind, actor.userId());
        if (sourceSnapshot != null) revision.attachSourceSnapshot(sourceSnapshot);
        revisions.save(revision);
        materialize(revision.getId(), templates);
        return saved;
    }

    // ---------------------------------------------------------------- revisions

    @Transactional
    public McpEntryRevision createDraftRevision(Long entryId, String description,
                                                List<McpProfileTemplate> templates,
                                                String sourceKind, String sourceSnapshot,
                                                String localCuration, McpActor actor) {
        McpServerEntry entry = lockedEntry(entryId);
        requireWritable(entry, actor);
        requireSourceKind(sourceKind);
        requireMetadataLength(description);
        requireMetadataLength(localCuration);
        requireSnapshotLength(sourceSnapshot);
        McpEntryRevision latest = revisions.findFirstByEntryIdOrderByRevisionNumberDesc(entryId).orElse(null);
        if (latest != null && (latest.getStatus() == McpRevisionStatus.DRAFT
                || latest.getStatus() == McpRevisionStatus.PENDING_REVIEW)) {
            throw new DomainConflictException("error.mcp.draftExists");
        }
        validateTemplates(templates);
        McpEntryRevision revision = new McpEntryRevision(
                entryId, latest == null ? 1 : latest.getRevisionNumber() + 1, sourceKind, actor.userId());
        revision.updateDraft(description, localCuration);
        if (sourceSnapshot != null) revision.attachSourceSnapshot(sourceSnapshot);
        McpEntryRevision saved = revisions.save(revision);
        materialize(saved.getId(), templates);
        return saved;
    }

    @Transactional
    public McpEntryRevision updateDraftRevision(Long revisionId, String description,
                                                List<McpProfileTemplate> templates,
                                                String localCuration, McpActor actor) {
        McpEntryRevision revision = lockedRevision(revisionId);
        requireWritable(requireEntry(revision.getEntryId()), actor);
        requireMetadataLength(description);
        requireMetadataLength(localCuration);
        validateTemplates(templates);
        revision.updateDraft(description, localCuration);
        profiles.deleteByRevisionId(revisionId);
        materialize(revisionId, templates);
        return revisions.save(revision);
    }

    @Transactional
    public McpEntryRevision deleteRevision(Long revisionId, McpActor actor) {
        McpEntryRevision revision = lockedRevision(revisionId);
        requireWritable(requireEntry(revision.getEntryId()), actor);
        revision.requireDeletable();
        profiles.deleteByRevisionId(revisionId);
        revisions.delete(revision);
        return revision;
    }

    // ---------------------------------------------------------------- review

    @Transactional
    public McpEntryRevision submitRevision(Long revisionId, McpActor actor) {
        McpEntryRevision revision = lockedRevision(revisionId);
        McpServerEntry entry = requireEntry(revision.getEntryId());
        requireWritable(entry, actor);
        revision.requireEditable();
        requirePublishedContent(revision);
        revision.submit();
        return revisions.save(revision);
    }

    @Transactional
    public McpEntryRevision withdrawRevision(Long revisionId, McpActor actor) {
        McpEntryRevision revision = lockedRevision(revisionId);
        if (actor.userId() == null || (!actor.superAdmin() && !actor.userId().equals(revision.getCreatedBy()))) {
            throw new DomainForbiddenException("error.mcp.permission");
        }
        revision.withdraw();
        return revisions.save(revision);
    }

    @Transactional
    public McpEntryRevision approveRevision(Long revisionId, String notes, McpActor actor) {
        McpEntryRevision revision = lockedRevision(revisionId);
        McpServerEntry entry = requireEntry(revision.getEntryId());
        if (!actor.canReview(entry)) throw new DomainForbiddenException("error.mcp.permission");
        requireActive(entry);
        requireNotesLength(notes);
        revision.approve();
        decisions.save(new McpReviewDecision(revisionId, actor.userId(), "APPROVED", notes));
        entry.pointToPublishedRevision(revision);
        entry.setUpdatedBy(actor.userId());
        entries.save(entry);
        return revisions.save(revision);
    }

    @Transactional
    public McpEntryRevision rejectRevision(Long revisionId, String notes, McpActor actor) {
        McpEntryRevision revision = lockedRevision(revisionId);
        McpServerEntry entry = requireEntry(revision.getEntryId());
        if (!actor.canReview(entry)) throw new DomainForbiddenException("error.mcp.permission");
        if (notes == null || notes.isBlank() || notes.length() > 10000) {
            throw new DomainBadRequestException("error.mcp.rejectReason");
        }
        revision.reject();
        decisions.save(new McpReviewDecision(revisionId, actor.userId(), "REJECTED", notes.trim()));
        return revisions.save(revision);
    }

    @Transactional
    public McpEntryRevision directPublishRevision(Long revisionId, String notes, McpActor actor) {
        if (!actor.superAdmin()) throw new DomainForbiddenException("error.mcp.permission");
        McpEntryRevision revision = lockedRevision(revisionId);
        McpServerEntry entry = requireEntry(revision.getEntryId());
        requireActive(entry);
        if (revision.getStatus() == McpRevisionStatus.DRAFT) {
            revision.requireEditable();
            requirePublishedContent(revision);
            revision.submit();
        }
        requireNotesLength(notes);
        revision.approve();
        decisions.save(new McpReviewDecision(revisionId, actor.userId(), "DIRECT_APPROVED", notes));
        entry.pointToPublishedRevision(revision);
        entry.setUpdatedBy(actor.userId());
        entries.save(entry);
        return revisions.save(revision);
    }

    // ---------------------------------------------------------------- entry maintenance & governance

    @Transactional
    public McpServerEntry renameEntry(Long entryId, String displayName, String summary, McpActor actor) {
        McpServerEntry entry = lockedEntry(entryId);
        requireWritable(entry, actor);
        entry.rename(displayName);
        requireMetadataLength(summary);
        entry.setSummary(summary);
        entry.setUpdatedBy(actor.userId());
        return entries.save(entry);
    }

    @Transactional
    public McpServerEntry govern(Long entryId, String action, McpVisibility visibility,
                                 String reason, McpActor actor) {
        McpServerEntry entry = lockedEntry(entryId);
        boolean maintain = actor.canMaintain(entry);
        if (!maintain && !actor.reviewer()) throw new DomainForbiddenException("error.mcp.permission");
        if (reason == null || reason.isBlank() || reason.length() > 10000) {
            throw new DomainBadRequestException("error.mcp.governanceReason");
        }
        if (Set.of("SHOW", "RESTORE", "VISIBILITY").contains(action)
                && namespaceOf(entry).getStatus() != NamespaceStatus.ACTIVE) {
            throw new DomainBadRequestException("error.mcp.namespaceInactive");
        }
        switch (action) {
            case "HIDE" -> entry.hide();
            case "SHOW" -> entry.show();
            case "ARCHIVE" -> entry.archive();
            case "RESTORE" -> entry.restore();
            case "VISIBILITY" -> {
                if (!maintain) throw new DomainForbiddenException("error.mcp.permission");
                entry.changeVisibility(visibility);
            }
            default -> throw new DomainBadRequestException("error.mcp.governanceAction");
        }
        entry.setUpdatedBy(actor.userId());
        return entries.save(entry);
    }

    // ---------------------------------------------------------------- guards

    public McpServerEntry requireWritable(McpServerEntry entry, McpActor actor) {
        requireActive(entry);
        if (!actor.canMaintain(entry)) throw new DomainForbiddenException("error.mcp.permission");
        return entry;
    }

    public void requireActive(McpServerEntry entry) {
        if (namespaceOf(entry).getStatus() != NamespaceStatus.ACTIVE
                || entry.getStatus() != McpEntryStatus.ACTIVE) {
            throw new DomainBadRequestException("error.mcp.namespaceInactive");
        }
    }

    public McpServerEntry lockedEntry(Long id) {
        return entries.findByIdForUpdate(id)
                .orElseThrow(() -> new DomainNotFoundException("error.mcp.notFound"));
    }

    public McpEntryRevision lockedRevision(Long id) {
        return revisions.findByIdForUpdate(id)
                .orElseThrow(() -> new DomainNotFoundException("error.mcp.revisionNotFound"));
    }

    public McpServerEntry requireEntry(Long id) {
        return entries.findById(id)
                .orElseThrow(() -> new DomainNotFoundException("error.mcp.notFound"));
    }

    private Namespace namespaceOf(McpServerEntry entry) {
        return namespaces.findById(entry.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("error.mcp.namespaceNotFound"));
    }

    private void requirePublishedContent(McpEntryRevision revision) {
        if (profiles.findByRevisionIdOrderByProfileKey(revision.getId()).isEmpty()) {
            throw new DomainBadRequestException("error.mcp.validationRequired");
        }
    }

    private void requireSourceKind(String sourceKind) {
        if (sourceKind == null || !SOURCE_KINDS.contains(sourceKind)) {
            throw new DomainBadRequestException("error.mcp.metadata");
        }
    }

    private void requireMetadataLength(String value) {
        if (value != null && value.length() > MAX_METADATA_LENGTH) {
            throw new DomainBadRequestException("error.mcp.metadata");
        }
    }

    private void requireSnapshotLength(String value) {
        if (value != null && value.length() > MAX_SNAPSHOT_LENGTH) {
            throw new DomainBadRequestException("error.mcp.metadata");
        }
    }

    private void requireNotesLength(String value) {
        if (value != null && value.length() > 10000) {
            throw new DomainBadRequestException("error.mcp.reviewNotes");
        }
    }

    // ---------------------------------------------------------------- profile templates

    private void validateTemplates(List<McpProfileTemplate> templates) {
        List<McpProfileTemplate> safe = templates == null ? List.of() : templates;
        Set<String> keys = new HashSet<>();
        for (McpProfileTemplate template : safe) {
            if (template == null) throw new DomainBadRequestException("error.mcp.profileInvalid");
            if (template.profileKey() == null || !template.profileKey().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
                    || !keys.add(template.profileKey())) {
                throw new DomainBadRequestException("error.mcp.profileInvalid");
            }
            if (template.displayName() == null || template.displayName().isBlank()
                    || template.displayName().length() > 200 || template.transportType() == null) {
                throw new DomainBadRequestException("error.mcp.profileInvalid");
            }
            String endpoint = blankToNull(template.endpointTemplate());
            String command = blankToNull(template.commandTemplate());
            if (endpoint != null && endpoint.length() > MAX_TEMPLATE_LENGTH
                    || command != null && command.length() > MAX_TEMPLATE_LENGTH) {
                throw new DomainBadRequestException("error.mcp.profileInvalid");
            }
            switch (template.transportType()) {
                case STDIO -> {
                    if (command == null) throw new DomainBadRequestException("error.mcp.profileInvalid");
                }
                case STREAMABLE_HTTP, LEGACY_HTTP_SSE -> {
                    if (endpoint == null) throw new DomainBadRequestException("error.mcp.profileInvalid");
                }
                case CUSTOM -> {
                    // Non-standard transports are stored descriptively; readers render them as manual configuration.
                }
            }
            validateArguments(template.arguments());
            // STDIO secrets bind through the generated env map, not the command text, so the
            // ${NAME} placeholder rule only applies to endpoint templates.
            String templateText = template.transportType() == McpTransportType.STDIO ? null : endpoint;
            validateVariables(template.variables(), templateText);
        }
    }

    private void validateArguments(List<String> arguments) {
        List<String> safe = arguments == null ? List.of() : arguments;
        if (safe.size() > 100) throw new DomainBadRequestException("error.mcp.profileInvalid");
        for (String argument : safe) {
            if (argument == null || argument.length() > 1000) {
                throw new DomainBadRequestException("error.mcp.profileInvalid");
            }
        }
    }

    private void validateVariables(List<McpVariableTemplate> variables, String templateText) {
        List<McpVariableTemplate> safe = variables == null ? List.of() : variables;
        if (safe.size() > 50) throw new DomainBadRequestException("error.mcp.profileInvalid");
        Set<String> names = new LinkedHashSet<>();
        for (McpVariableTemplate variable : safe) {
            if (variable == null || variable.name() == null
                    || !variable.name().matches("[A-Za-z_][A-Za-z0-9_]*") || !names.add(variable.name())) {
                throw new DomainBadRequestException("error.mcp.profileInvalid");
            }
            if (variable.description() != null && variable.description().length() > 1000
                    || variable.validationHint() != null && variable.validationHint().length() > 1000) {
                throw new DomainBadRequestException("error.mcp.profileInvalid");
            }
            if (variable.defaultValue() != null && variable.defaultValue().length() > 1000) {
                throw new DomainBadRequestException("error.mcp.profileInvalid");
            }
            if (variable.secret() && variable.defaultValue() != null) {
                throw new DomainBadRequestException("error.mcp.secretDefault");
            }
            if (variable.secret() && templateText != null
                    && !templateText.contains("${" + variable.name() + "}")) {
                throw new DomainBadRequestException("error.mcp.secretPlaceholder");
            }
        }
    }

    /** Canonical JSON mirrors what the read side parses in JpaCapabilityCatalogQueryRepository. */
    private void materialize(Long revisionId, List<McpProfileTemplate> templates) {
        List<McpProfileTemplate> safe = templates == null ? List.of() : templates;
        for (McpProfileTemplate template : safe) {
            McpConnectionProfile profile = new McpConnectionProfile(
                    revisionId, template.profileKey(), template.displayName(), template.transportType());
            profile.updateTemplates(
                    blankToNull(template.endpointTemplate()), blankToNull(template.commandTemplate()),
                    writeArray(template.arguments()), writeVariables(template.variables()));
            profiles.save(profile);
        }
    }

    private String writeArray(List<String> arguments) {
        try {
            var array = objectMapper.createArrayNode();
            for (String argument : arguments == null ? List.<String>of() : arguments) array.add(argument);
            return objectMapper.writeValueAsString(array);
        } catch (JsonProcessingException exception) {
            throw new DomainBadRequestException("error.mcp.profileInvalid");
        }
    }

    private String writeVariables(List<McpVariableTemplate> variables) {
        try {
            var array = objectMapper.createArrayNode();
            for (McpVariableTemplate variable : variables == null ? List.<McpVariableTemplate>of() : variables) {
                var node = objectMapper.createObjectNode();
                node.put("name", variable.name());
                if (variable.description() != null) node.put("description", variable.description());
                node.put("required", variable.required());
                node.put("secret", variable.secret());
                if (!variable.secret() && variable.defaultValue() != null) {
                    node.put("defaultValue", variable.defaultValue());
                }
                if (variable.validationHint() != null) node.put("validationHint", variable.validationHint());
                array.add(node);
            }
            return objectMapper.writeValueAsString(array);
        } catch (JsonProcessingException exception) {
            throw new DomainBadRequestException("error.mcp.profileInvalid");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
