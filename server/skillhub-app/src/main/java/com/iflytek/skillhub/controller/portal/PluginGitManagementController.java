package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitIntentView;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitSettingsView;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitTargetRequest;
import com.iflytek.skillhub.dto.plugin.PluginGitDtos.GitTargetView;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.plugin.PluginGitManagementAppService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plugin-management")
public class PluginGitManagementController extends BaseApiController {
    private final PluginGitManagementAppService app;

    public PluginGitManagementController(
            ApiResponseFactory responses, PluginGitManagementAppService app) {
        super(responses);
        this.app = app;
    }

    @GetMapping("/git/settings")
    @Operation(summary = "Read allowed internal Git destinations and named publisher references")
    public ApiResponse<GitSettingsView> settings(HttpServletRequest http) {
        return ok("response.success.read", app.settings(PluginManagementController.actor(http)));
    }

    @PostMapping("/git/targets")
    @Operation(summary = "Append namespace Git target revision without retargeting approved plans")
    public ApiResponse<GitTargetView> configure(
            @Valid @RequestBody GitTargetRequest request, HttpServletRequest http) {
        return ok(
                "response.success.created",
                app.configure(
                        request,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http)));
    }

    @GetMapping("/releases/{id}/git-intents")
    @Operation(summary = "Read immutable pre-push Git intents and commit evidence")
    public ApiResponse<List<GitIntentView>> intents(
            @PathVariable Long id, HttpServletRequest http) {
        return ok("response.success.read", app.intents(id, PluginManagementController.actor(http)));
    }
}
