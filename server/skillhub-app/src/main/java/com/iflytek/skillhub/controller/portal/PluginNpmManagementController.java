package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.ChannelView;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.DependencyUploadView;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.PrerequisitesView;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.RegistryRequest;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.RegistrySelection;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.RegistryView;
import com.iflytek.skillhub.dto.plugin.PluginNpmDtos.SettingsView;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.plugin.PluginNpmManagementAppService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/plugin-management")
public class PluginNpmManagementController extends BaseApiController {
    private final PluginNpmManagementAppService app;

    public PluginNpmManagementController(
            ApiResponseFactory responses, PluginNpmManagementAppService app) {
        super(responses);
        this.app = app;
    }

    @GetMapping("/npm/settings")
    @Operation(summary = "Read allowed internal registries and publisher credential reference IDs")
    public ApiResponse<SettingsView> settings(HttpServletRequest http) {
        return ok("response.success.read", app.settings(PluginManagementController.actor(http)));
    }

    @GetMapping("/npm/registries")
    @Operation(summary = "List configured npm destinations for draft selection")
    public ApiResponse<List<RegistryView>> registries(HttpServletRequest http) {
        return ok("response.success.read", app.registries(PluginManagementController.actor(http)));
    }

    @PostMapping("/npm/registries")
    @Operation(summary = "Append immutable internal npm registry configuration revision")
    public ApiResponse<RegistryView> configure(
            @Valid @RequestBody RegistryRequest request, HttpServletRequest http) {
        return ok(
                "response.success.created",
                app.configure(
                        request,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @PutMapping("/releases/{id}/npm-registry")
    @Operation(summary = "Select configured npm registry for draft Pi distribution")
    public ApiResponse<RegistryView> select(
            @PathVariable Long id,
            @Valid @RequestBody RegistrySelection request,
            HttpServletRequest http) {
        return ok(
                "response.success.updated",
                app.select(
                        id,
                        request.registryId(),
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @GetMapping("/npm/dependencies")
    @Operation(summary = "List independent dependency TGZ uploads and publication receipts")
    public ApiResponse<List<DependencyUploadView>> uploads(HttpServletRequest http) {
        return ok("response.success.read", app.uploads(PluginManagementController.actor(http)));
    }

    @PostMapping(value = "/npm/dependencies", consumes = "application/octet-stream")
    @Operation(
            summary =
                    "Upload one bounded original npm dependency TGZ for administrator confirmation",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @io.swagger.v3.oas.annotations.media.Content(
                                            mediaType = "application/octet-stream",
                                            schema =
                                                    @io.swagger.v3.oas.annotations.media.Schema(
                                                            type = "string",
                                                            format = "binary"))))
    public ApiResponse<DependencyUploadView> upload(
            @RequestParam Long registryId, @RequestParam String source, HttpServletRequest http)
            throws IOException {
        return ok(
                "response.success.created",
                app.upload(
                        registryId,
                        source,
                        http.getInputStream(),
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @PostMapping("/npm/dependencies/{id}/publish")
    @Operation(
            summary =
                    "Confirm or retry independent dependency publication without running lifecycle"
                            + " scripts")
    public ApiResponse<DependencyUploadView> publish(@PathVariable Long id, HttpServletRequest http)
            throws IOException {
        return ok(
                "response.success.updated",
                app.publishDependency(
                        id,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @GetMapping("/releases/{id}/publication-channels")
    @Operation(summary = "Read immutable approved channel plans and durable receipts")
    public ApiResponse<List<ChannelView>> channels(@PathVariable Long id, HttpServletRequest http) {
        return ok(
                "response.success.read", app.channels(id, PluginManagementController.actor(http)));
    }

    @PostMapping("/releases/{id}/publish-channels")
    @Operation(summary = "Retry incomplete approved channels while retaining successful receipts")
    public ApiResponse<List<ChannelView>> retry(@PathVariable Long id, HttpServletRequest http) {
        return ok(
                "response.success.updated",
                app.retry(
                        id,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @PostMapping("/releases/{id}/npm-prerequisites")
    @Operation(
            summary =
                    "Check direct npm dependency availability against the fixed internal registry"
                            + " only")
    public ApiResponse<PrerequisitesView> prerequisites(
            @PathVariable Long id, HttpServletRequest http) throws IOException {
        return ok(
                "response.success.read",
                app.prerequisites(id, PluginManagementController.actor(http)));
    }
}
