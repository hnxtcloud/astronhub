package com.iflytek.skillhub.service.mcp;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.mcp.McpProfileTemplate;
import com.iflytek.skillhub.domain.mcp.McpServerEntry;
import com.iflytek.skillhub.domain.mcp.McpVariableTemplate;
import com.iflytek.skillhub.domain.mcp.service.McpActor;
import com.iflytek.skillhub.domain.mcp.service.McpCatalogWorkflow;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpEntryCreateRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpEntryUpdateRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpGovernanceRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpProfileInput;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpRejectRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpReviewRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpRevisionCreateRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpRevisionUpdateRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpVariableInput;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpEntryView;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpRevisionView;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.McpManagementQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates MCP catalog publication, review, governance, and server.json import. */
@Service
public class McpManagementAppService {
    private final McpCatalogWorkflow workflow;
    private final ServerJsonImportParser parser;
    private final McpManagementQueryRepository queries;
    private final AuditLogService audit;
    private final RequestIdAccessor requestIds;

    public McpManagementAppService(McpCatalogWorkflow workflow, ServerJsonImportParser parser,
            McpManagementQueryRepository queries, AuditLogService audit, RequestIdAccessor requestIds) {
        this.workflow = workflow;
        this.parser = parser;
        this.queries = queries;
        this.audit = audit;
        this.requestIds = requestIds;
    }

    @Transactional
    public McpEntryView createEntry(McpEntryCreateRequest request, McpActor actor, AuditRequestContext context) {
        ServerJsonImportParser.ServerJsonImport imported =
                request.serverJson() == null ? null : parser.parse(request.serverJson());
        List<McpProfileTemplate> templates = imported == null
                ? toTemplates(request.profiles())
                : mergeTemplates(request.profiles(), imported.profiles());
        String summary = isBlank(request.summary()) && imported != null ? imported.summary() : request.summary();
        String sourceKind = imported == null
                ? McpCatalogWorkflow.SOURCE_MANUAL
                : McpCatalogWorkflow.SOURCE_SERVER_JSON;
        McpServerEntry entry = workflow.createEntry(
                request.namespaceId(), request.slug(), request.displayName(), summary, request.visibility(),
                templates, sourceKind, imported == null ? null : imported.snapshotJson(), actor);
        record("mcp.entry.create", entry.getId(), "MCP_ENTRY", actor, context, "{}");
        return queries.detail(entry.getId(), actor);
    }

    @Transactional
    public McpEntryView updateEntry(Long entryId, McpEntryUpdateRequest request, McpActor actor,
                                    AuditRequestContext context) {
        workflow.renameEntry(entryId, request.displayName(), request.summary(), actor);
        record("mcp.entry.update", entryId, "MCP_ENTRY", actor, context, "{}");
        return queries.detail(entryId, actor);
    }

    @Transactional
    public McpRevisionView createRevision(Long entryId, McpRevisionCreateRequest request, McpActor actor,
                                          AuditRequestContext context) {
        ServerJsonImportParser.ServerJsonImport imported =
                request.serverJson() == null ? null : parser.parse(request.serverJson());
        List<McpProfileTemplate> templates = imported == null
                ? toTemplates(request.profiles())
                : mergeTemplates(request.profiles(), imported.profiles());
        String sourceKind = imported == null
                ? McpCatalogWorkflow.SOURCE_MANUAL
                : McpCatalogWorkflow.SOURCE_SERVER_JSON;
        var revision = workflow.createDraftRevision(entryId, request.description(), templates, sourceKind,
                imported == null ? null : imported.snapshotJson(), request.localCuration(), actor);
        record("mcp.revision.create", revision.getId(), "MCP_REVISION", actor, context, "{}");
        return queries.revision(revision.getId(), actor);
    }

    @Transactional
    public McpRevisionView updateRevision(Long revisionId, McpRevisionUpdateRequest request, McpActor actor,
                                          AuditRequestContext context) {
        var revision = workflow.updateDraftRevision(
                revisionId, request.description(), toTemplates(request.profiles()), request.localCuration(), actor);
        record("mcp.revision.update", revisionId, "MCP_REVISION", actor, context, "{}");
        return queries.revision(revisionId, actor);
    }

    @Transactional
    public McpRevisionView deleteRevision(Long revisionId, McpActor actor, AuditRequestContext context) {
        var revision = workflow.deleteRevision(revisionId, actor);
        record("mcp.revision.delete", revisionId, "MCP_REVISION", actor, context, "{}");
        return queries.revision(revision.getId(), actor);
    }

    @Transactional
    public McpRevisionView submit(Long revisionId, McpActor actor, AuditRequestContext context) {
        var revision = workflow.submitRevision(revisionId, actor);
        record("mcp.revision.submit", revisionId, "MCP_REVISION", actor, context, "{}");
        return queries.revision(revisionId, actor);
    }

    @Transactional
    public McpRevisionView withdraw(Long revisionId, McpActor actor, AuditRequestContext context) {
        var revision = workflow.withdrawRevision(revisionId, actor);
        record("mcp.revision.withdraw", revisionId, "MCP_REVISION", actor, context, "{}");
        return queries.revision(revisionId, actor);
    }

    @Transactional
    public McpRevisionView approve(Long revisionId, McpReviewRequest request, McpActor actor,
                                   AuditRequestContext context) {
        var revision = workflow.approveRevision(revisionId, request.notes(), actor);
        record("mcp.revision.approve", revisionId, "MCP_REVISION", actor, context, "{}");
        return queries.revision(revisionId, actor);
    }

    @Transactional
    public McpRevisionView reject(Long revisionId, McpRejectRequest request, McpActor actor,
                                  AuditRequestContext context) {
        var revision = workflow.rejectRevision(revisionId, request.notes(), actor);
        record("mcp.revision.reject", revisionId, "MCP_REVISION", actor, context, "{}");
        return queries.revision(revisionId, actor);
    }

    @Transactional
    public McpRevisionView directPublish(Long revisionId, McpReviewRequest request, McpActor actor,
                                         AuditRequestContext context) {
        var revision = workflow.directPublishRevision(revisionId, request.notes(), actor);
        record("mcp.revision.directPublish", revisionId, "MCP_REVISION", actor, context, "{}");
        return queries.revision(revisionId, actor);
    }

    @Transactional
    public McpEntryView govern(Long entryId, McpGovernanceRequest request, McpActor actor,
                               AuditRequestContext context) {
        McpServerEntry entry = workflow.govern(entryId, request.action(), request.visibility(), request.reason(), actor);
        record("mcp.entry.govern", entryId, "MCP_ENTRY", actor, context, "{\"action\":\"" + request.action() + "\"}");
        return queries.detail(entry.getId(), actor);
    }

    /** Request profiles win when provided; otherwise the imported templates stand in. */
    private List<McpProfileTemplate> mergeTemplates(List<McpProfileInput> requested,
                                                    List<McpProfileTemplate> imported) {
        return requested == null || requested.isEmpty() ? imported : toTemplates(requested);
    }

    private List<McpProfileTemplate> toTemplates(List<McpProfileInput> inputs) {
        if (inputs == null) return List.of();
        return inputs.stream()
                .map(input -> new McpProfileTemplate(
                        input.profileKey(), input.displayName(), input.transportType(),
                        input.endpointTemplate(), input.commandTemplate(),
                        input.arguments() == null ? List.of() : input.arguments(),
                        toVariables(input.variables())))
                .toList();
    }

    private List<McpVariableTemplate> toVariables(List<McpVariableInput> inputs) {
        if (inputs == null) return List.of();
        return inputs.stream()
                .map(input -> new McpVariableTemplate(
                        input.name(), input.description(),
                        Boolean.TRUE.equals(input.required()), Boolean.TRUE.equals(input.secret()),
                        input.defaultValue(), input.validationHint()))
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void record(String action, Long id, String targetType, McpActor actor,
                        AuditRequestContext context, String detailJson) {
        audit.record(
                actor.userId(),
                action,
                targetType,
                id,
                requestIds.current(),
                context.clientIp(),
                context.userAgent(),
                detailJson);
    }
}
