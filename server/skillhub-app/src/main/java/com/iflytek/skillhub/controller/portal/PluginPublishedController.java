package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.dto.plugin.PluginAccessDtos.*;
import com.iflytek.skillhub.repository.PluginPublishedQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.plugin.*;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/plugins/{namespace}/{slug}/versions")
public class PluginPublishedController extends BaseApiController {
    private final PluginPublishedQueryRepository queries;
    private final PluginAccessAppService app;

    public PluginPublishedController(
            ApiResponseFactory responses,
            PluginPublishedQueryRepository queries,
            PluginAccessAppService app) {
        super(responses);
        this.queries = queries;
        this.app = app;
    }

    @GetMapping
    @Operation(summary = "Read an authorized plugin and its published version history")
    public ApiResponse<PublishedPluginView> history(
            @PathVariable String namespace, @PathVariable String slug, HttpServletRequest http) {
        return ok(
                "response.success.read",
                queries.detail(namespace, slug, PluginManagementController.actor(http)));
    }

    @GetMapping("/{version}")
    @Operation(summary = "Read an immutable published plugin version without storage secrets")
    public ApiResponse<PublishedPluginReleaseView> detail(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            HttpServletRequest http) {
        return ok(
                "response.success.read",
                queries.detail(namespace, slug, version, PluginManagementController.actor(http)));
    }

    @GetMapping("/{version}/files")
    @Operation(summary = "List inventoried files with the archive's current access policy")
    public ApiResponse<PluginFileListView> files(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestParam(defaultValue = "claude-code") String runtime,
            HttpServletRequest http) {
        var actor = PluginManagementController.actor(http);
        return ok(
                "response.success.read",
                app.files(
                        app.resolve(namespace, slug, version, actor).getId(),
                        runtime,
                        false,
                        actor));
    }

    @GetMapping("/{version}/file")
    @Operation(summary = "Read a bounded inert UTF-8 preview of an exact inventoried file")
    public ApiResponse<PluginFileContentView> file(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestParam(defaultValue = "claude-code") String runtime,
            @RequestParam String path,
            HttpServletRequest http)
            throws IOException {
        var actor = PluginManagementController.actor(http);
        return ok(
                "response.success.read",
                app.file(
                        app.resolve(namespace, slug, version, actor).getId(),
                        runtime,
                        path,
                        false,
                        actor,
                        AuditRequestContext.from(http)));
    }

    @GetMapping("/{version}/install")
    @Operation(
            summary =
                    "Generate audited local instructions for a fixed version without claiming a"
                        + " runtime test")
    public ApiResponse<PluginInstallView> install(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestParam(defaultValue = "claude-code") String runtime,
            HttpServletRequest http) {
        var actor = PluginManagementController.actor(http);
        return ok(
                "response.success.read",
                app.install(
                        app.resolve(namespace, slug, version, actor).getId(),
                        runtime,
                        actor,
                        AuditRequestContext.from(http)));
    }

    @GetMapping("/{version}/download")
    @Operation(summary = "Download an authenticated authorized fixed published plugin archive")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestParam(defaultValue = "claude-code") String runtime,
            HttpServletRequest http) {
        var download =
                app.download(
                        namespace,
                        slug,
                        version,
                        runtime,
                        PluginManagementController.actor(http),
                        AuditRequestContext.from(http));
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                download.filename().endsWith(".tgz")
                                        ? "application/gzip"
                                        : "application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(download.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-SHA256", download.sha256())
                .body(new InputStreamResource(download.stream()));
    }
}
