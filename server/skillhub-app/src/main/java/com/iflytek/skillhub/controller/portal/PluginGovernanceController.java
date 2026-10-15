package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.dto.plugin.PluginAccessDtos.*;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.*;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.plugin.*;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plugin-management")
public class PluginGovernanceController extends BaseApiController {
    private final PluginGovernanceAppService governance;
    private final PluginAccessAppService access;

    public PluginGovernanceController(
            ApiResponseFactory responses,
            PluginGovernanceAppService governance,
            PluginAccessAppService access) {
        super(responses);
        this.governance = governance;
        this.access = access;
    }

    @PostMapping("/projects/{id}/governance")
    @Operation(
            summary =
                    "Govern catalog visibility/state without deleting external copies or changing"
                        + " publication snapshots")
    public ApiResponse<PluginProjectView> govern(
            @PathVariable Long id,
            @Valid @RequestBody PluginGovernanceRequest request,
            HttpServletRequest http) {
        return ok(
                "response.success.updated",
                governance.govern(
                        id,
                        request,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @PostMapping("/releases/{id}/yank")
    @Operation(
            summary = "Withdraw a published version and reselect current by durable approval order")
    public ApiResponse<PluginReleaseView> yank(
            @PathVariable Long id,
            @Valid @RequestBody PluginYankRequest request,
            HttpServletRequest http) {
        return ok(
                "response.success.updated",
                governance.yank(
                        id,
                        request,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @GetMapping("/projects/{id}/components")
    @Operation(summary = "Read independently authorized fixed-version component links")
    public ApiResponse<java.util.List<PluginLinkedComponentView>> components(
            @PathVariable Long id, HttpServletRequest http) {
        return ok(
                "response.success.read",
                governance.components(id, PluginManagementController.actor(http)));
    }

    @PostMapping("/projects/{id}/components")
    @Operation(
            summary =
                    "Link an existing independently authorized component without creating or owning"
                        + " it")
    public ApiResponse<java.util.List<PluginLinkedComponentView>> link(
            @PathVariable Long id,
            @Valid @RequestBody PluginComponentLinkRequest request,
            HttpServletRequest http) {
        return ok(
                "response.success.updated",
                governance.link(
                        id,
                        request,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @DeleteMapping("/projects/{id}/components/{linkId}")
    @Operation(summary = "Remove metadata association without changing its independent target")
    public ApiResponse<java.util.List<PluginLinkedComponentView>> unlink(
            @PathVariable Long id, @PathVariable Long linkId, HttpServletRequest http) {
        return ok(
                "response.success.updated",
                governance.unlink(
                        id,
                        linkId,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @GetMapping("/releases/{id}/files")
    @Operation(
            summary =
                    "List draft or historical package files as an authorized maintainer or"
                        + " reviewer")
    public ApiResponse<PluginFileListView> files(
            @PathVariable Long id,
            @RequestParam(defaultValue = "claude-code") String runtime,
            HttpServletRequest http) {
        return ok(
                "response.success.read",
                access.files(id, runtime, true, PluginManagementController.actor(http)));
    }

    @GetMapping("/releases/{id}/file")
    @Operation(summary = "Inspect a safe inert text file as an authorized maintainer or reviewer")
    public ApiResponse<PluginFileContentView> file(
            @PathVariable Long id,
            @RequestParam(defaultValue = "claude-code") String runtime,
            @RequestParam String path,
            HttpServletRequest http)
            throws java.io.IOException {
        return ok(
                "response.success.read",
                access.file(
                        id,
                        runtime,
                        path,
                        true,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }
}
