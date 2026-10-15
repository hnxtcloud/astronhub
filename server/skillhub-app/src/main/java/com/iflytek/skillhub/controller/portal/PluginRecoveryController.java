package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.dto.plugin.PluginRecoveryDtos.*;
import com.iflytek.skillhub.repository.PluginRecoveryQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.plugin.PluginRecoveryAppService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plugin-management/releases/{id}")
public class PluginRecoveryController extends BaseApiController {
    private final PluginRecoveryAppService app;
    private final PluginRecoveryQueryRepository queries;

    public PluginRecoveryController(
            ApiResponseFactory responses,
            PluginRecoveryAppService app,
            PluginRecoveryQueryRepository queries) {
        super(responses);
        this.app = app;
        this.queries = queries;
    }

    @GetMapping("/publication-recovery")
    @Operation(
            summary = "Inspect original and selected channel plans with immutable revision history")
    public ApiResponse<List<RecoveryChannelView>> read(
            @PathVariable Long id, HttpServletRequest http) {
        return ok(
                "response.success.read", queries.read(id, PluginManagementController.actor(http)));
    }

    @PostMapping("/publication-replans/preview")
    @Operation(
            summary =
                    "Preview an explicit replacement target for a blocked or definitively failed"
                            + " channel")
    public ApiResponse<ReplanPreview> preview(
            @PathVariable Long id,
            @Valid @RequestBody ReplanRequest request,
            HttpServletRequest http) {
        return ok(
                "response.success.read",
                app.preview(id, request, PluginManagementController.actor(http)));
    }

    @PostMapping("/publication-replans")
    @Operation(
            summary =
                    "Apply a previewed audited plan revision without changing approval or"
                            + " successful channels")
    public ApiResponse<List<RecoveryChannelView>> apply(
            @PathVariable Long id,
            @Valid @RequestBody ReplanRequest request,
            HttpServletRequest http) {
        return ok(
                "response.success.updated",
                app.apply(
                        id,
                        request,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @PostMapping("/channel-governance/preview")
    @Operation(summary = "Preview explicit native index withdrawal and retained remote history")
    public ApiResponse<GovernancePreview> governancePreview(
            @PathVariable Long id,
            @Valid @RequestBody GovernanceRequest request,
            HttpServletRequest http) {
        return ok(
                "response.success.read",
                app.previewGovernance(id, request, PluginManagementController.actor(http)));
    }

    @PostMapping("/channel-governance")
    @Operation(summary = "Queue audited withdrawal without replacing publication evidence")
    public ApiResponse<List<RecoveryChannelView>> governance(
            @PathVariable Long id,
            @Valid @RequestBody GovernanceRequest request,
            HttpServletRequest http) {
        return ok(
                "response.success.updated",
                app.applyGovernance(
                        id,
                        request,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @GetMapping("/publication-attempts")
    @Operation(summary = "Page durable publication and withdrawal attempt history")
    public ApiResponse<AttemptPage> attempts(
            @PathVariable Long id,
            @RequestParam String channel,
            @RequestParam(defaultValue = "0") int page,
            HttpServletRequest http) {
        return ok(
                "response.success.read",
                queries.attemptHistory(id, channel, page, PluginManagementController.actor(http)));
    }

    @PostMapping("/channel-governance/{operationId}/retry")
    @Operation(summary = "Retry or reconcile a withdrawal at its original immutable target")
    public ApiResponse<List<RecoveryChannelView>> retry(
            @PathVariable Long id, @PathVariable Long operationId, HttpServletRequest http) {
        return ok("response.success.updated", app.retryGovernance(id, operationId,
                PluginManagementController.actor(http), AuditRequestContext.from(http)));
    }
}
