package com.iflytek.skillhub.service.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.plugin.service.*;
import com.iflytek.skillhub.dto.plugin.PluginAccessDtos.*;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.*;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.PluginComponentQueryRepository;
import com.iflytek.skillhub.repository.PluginManagementQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class PluginGovernanceAppService {
    private final PluginGovernanceWorkflow workflow;
    private final PluginManagementQueryRepository queries;
    private final AuditLogService audit;
    private final RequestIdAccessor requestIds;
    private final ObjectMapper mapper;
    private final PluginComponentQueryRepository components;

    public PluginGovernanceAppService(
            PluginGovernanceWorkflow workflow,
            PluginManagementQueryRepository queries,
            AuditLogService audit,
            RequestIdAccessor requestIds,
            ObjectMapper mapper,
            PluginComponentQueryRepository components) {
        this.workflow = workflow;
        this.queries = queries;
        this.audit = audit;
        this.requestIds = requestIds;
        this.mapper = mapper;
        this.components = components;
    }

    @Transactional
    public PluginProjectView govern(
            Long id,
            PluginGovernanceRequest request,
            PluginActor actor,
            AuditRequestContext context) {
        workflow.govern(id, request.action(), request.visibility(), request.reason(), actor);
        record(
                "plugin.project." + request.action().toLowerCase(java.util.Locale.ROOT),
                "PLUGIN_PROJECT",
                id,
                request.reason(),
                actor,
                context);
        return queries.detail(id, actor);
    }

    @Transactional
    public PluginReleaseView yank(
            Long id, PluginYankRequest request, PluginActor actor, AuditRequestContext context) {
        workflow.yank(id, request.reason(), actor);
        record("plugin.release.yank", "PLUGIN_RELEASE", id, request.reason(), actor, context);
        return queries.release(id, actor);
    }

    @Transactional(readOnly = true)
    public java.util.List<PluginLinkedComponentView> components(Long id, PluginActor actor) {
        queries.detail(id, actor);
        return components.list(id, actor);
    }

    @Transactional
    public java.util.List<PluginLinkedComponentView> link(
            Long id,
            PluginComponentLinkRequest request,
            PluginActor actor,
            AuditRequestContext context) {
        workflow.lockMaintained(id, actor);
        Long target = components.resolve(request, actor);
        workflow.link(id, request.kind(), target, actor);
        record(
                "plugin.component.link",
                "PLUGIN_PROJECT",
                id,
                request.kind() + ":" + target,
                actor,
                context);
        return components.list(id, actor);
    }

    @Transactional
    public java.util.List<PluginLinkedComponentView> unlink(
            Long id, Long linkId, PluginActor actor, AuditRequestContext context) {
        workflow.unlink(id, linkId, actor);
        record("plugin.component.unlink", "PLUGIN_PROJECT", id, "link:" + linkId, actor, context);
        return components.list(id, actor);
    }

    private void record(
            String action,
            String type,
            Long id,
            String reason,
            PluginActor actor,
            AuditRequestContext context) {
        try {
            audit.record(
                    actor.userId(),
                    action,
                    type,
                    id,
                    requestIds.current(),
                    context.clientIp(),
                    context.userAgent(),
                    mapper.writeValueAsString(Map.of("reason", reason)));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
