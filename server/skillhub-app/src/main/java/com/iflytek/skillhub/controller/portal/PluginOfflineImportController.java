package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.dto.plugin.PluginImportDtos.*;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.plugin.*;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/plugin-management")
public class PluginOfflineImportController extends BaseApiController {
    private final PluginOfflineImportAppService app;

    public PluginOfflineImportController(
            ApiResponseFactory responses, PluginOfflineImportAppService app) {
        super(responses);
        this.app = app;
    }

    @GetMapping("/import-settings")
    @Operation(summary = "Read current offline plugin import budgets")
    public ApiResponse<PluginImportLimits> limits() {
        return ok("response.success.read", app.limits());
    }

    @PutMapping("/import-settings")
    @Operation(summary = "Configure persisted offline import budgets as plugin administrator")
    public ApiResponse<PluginImportLimits> configure(
            @Valid @RequestBody PluginImportLimits limits, HttpServletRequest http) {
        return ok(
                "response.success.updated",
                app.configure(
                        limits,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @PostMapping(value = "/imports", consumes = "application/octet-stream")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/octet-stream",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary")))
    @Operation(
            summary =
                    "Upload ZIP or TGZ for bounded offline candidate preview without running"
                        + " scripts")
    public ApiResponse<ImportPreviewView> preview(
            @RequestParam Long namespaceId,
            @RequestParam(defaultValue = "false") boolean repository,
            @RequestParam String format,
            HttpServletRequest http)
            throws IOException {
        return ok(
                "response.success.created",
                app.preview(
                        namespaceId,
                        repository,
                        format,
                        http.getInputStream(),
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @GetMapping("/imports/{id}")
    @Operation(summary = "Read an owned offline import preview")
    public ApiResponse<ImportPreviewView> get(@PathVariable String id, HttpServletRequest http) {
        return ok("response.success.read", app.get(id, PluginManagementController.actor(http)));
    }

    @PostMapping("/imports/{id}/apply")
    @Operation(
            summary =
                    "Create selected plugin drafts with isolated results and immutable content"
                        + " conflicts")
    public ApiResponse<ImportResultsView> apply(
            @PathVariable String id,
            @Valid @RequestBody ImportSelectionRequest request,
            HttpServletRequest http) {
        return ok(
                "response.success.created",
                app.apply(
                        id,
                        request,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }
}
