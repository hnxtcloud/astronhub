package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.dto.plugin.PluginManagementDtos.*;
import com.iflytek.skillhub.repository.PluginManagementQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.plugin.PluginManagementAppService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Session-authenticated plugin management transport. Package keys/evidence are never request fields. */
@RestController
@RequestMapping("/api/v1/plugin-management")
public class PluginManagementController extends BaseApiController {
    private final PluginManagementAppService app;
    private final PluginManagementQueryRepository queries;
    public PluginManagementController(ApiResponseFactory responses, PluginManagementAppService app,
                                      PluginManagementQueryRepository queries) {
        super(responses); this.app = app; this.queries = queries;
    }

    @GetMapping("/projects")
    @Operation(summary = "List manageable plugin projects or the platform review queue")
    public ApiResponse<PageResponse<PluginProjectView>> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "false") boolean pendingReview,
            HttpServletRequest http) {
        return ok("response.success.read", queries.list(actor(http), page, size, pendingReview));
    }
    @PostMapping("/projects")
    @Operation(summary = "Create a namespace plugin project")
    public ApiResponse<PluginProjectView> create(@Valid @RequestBody PluginProjectCreateRequest request, HttpServletRequest http) {
        return ok("response.success.created", app.create(request, actor(http), AuditRequestContext.from(http)));
    }
    @GetMapping("/projects/{id}")
    @Operation(summary = "Read project drafts, immutable releases, review evidence and publication status")
    public ApiResponse<PluginProjectView> detail(@PathVariable Long id, HttpServletRequest http) {
        return ok("response.success.read", queries.detail(id, actor(http)));
    }
    @PatchMapping("/projects/{id}")
    @Operation(summary = "Edit catalog metadata without changing native archives")
    public ApiResponse<PluginProjectView> update(@PathVariable Long id, @Valid @RequestBody PluginProjectUpdateRequest request, HttpServletRequest http) {
        return ok("response.success.updated", app.update(id, request, actor(http), AuditRequestContext.from(http)));
    }
    @PostMapping("/projects/{id}/releases")
    @Operation(summary = "Create a new immutable version identity with editable draft metadata")
    public ApiResponse<PluginReleaseView> release(@PathVariable Long id, @Valid @RequestBody PluginReleaseCreateRequest request, HttpServletRequest http) {
        return ok("response.success.created", app.createRelease(id, request, actor(http), AuditRequestContext.from(http)));
    }
    @GetMapping("/releases/{id}")
    @Operation(summary = "Read a manageable plugin release and evidence")
    public ApiResponse<PluginReleaseView> releaseDetail(@PathVariable Long id, HttpServletRequest http) {
        return ok("response.success.read", queries.release(id, actor(http)));
    }
    @PatchMapping("/releases/{id}")
    @Operation(summary = "Edit a draft changelog")
    public ApiResponse<PluginReleaseView> draft(@PathVariable Long id, @Valid @RequestBody PluginDraftUpdateRequest request, HttpServletRequest http) {
        return ok("response.success.updated", app.updateDraft(id, request, actor(http), AuditRequestContext.from(http)));
    }
    @PostMapping(value = "/releases/{id}/archive", consumes = "application/zip")
    @Operation(summary = "Import a bounded Claude Code ZIP and validate its real native manifest")
    public ApiResponse<PluginReleaseView> archive(@PathVariable Long id, HttpServletRequest http) throws IOException {
        return ok("response.success.created", app.importArchive(id, http.getInputStream(), actor(http), AuditRequestContext.from(http)));
    }
    @PostMapping("/releases/{id}/submit")
    @Operation(summary = "Submit a draft with validated archives for human review")
    public ApiResponse<PluginReleaseView> submit(@PathVariable Long id, HttpServletRequest http) {
        return ok("response.success.updated", app.submit(id, actor(http), AuditRequestContext.from(http)));
    }
    @PostMapping("/releases/{id}/approve")
    @Operation(summary = "Approve a plugin release and create its independent durable publication task")
    public ApiResponse<PluginReleaseView> approve(@PathVariable Long id, @Valid @RequestBody PluginReviewRequest request, HttpServletRequest http) {
        return ok("response.success.updated", app.review(id, request, true, false, actor(http), AuditRequestContext.from(http)));
    }
    @PostMapping("/releases/{id}/reject")
    @Operation(summary = "Reject a pending release with human review evidence")
    public ApiResponse<PluginReleaseView> reject(@PathVariable Long id, @Valid @RequestBody PluginReviewRequest request, HttpServletRequest http) {
        return ok("response.success.updated", app.review(id, request, false, false, actor(http), AuditRequestContext.from(http)));
    }
    @PostMapping("/releases/{id}/direct-publish")
    @Operation(summary = "Super-admin approval of a validated draft, followed by independent publication")
    public ApiResponse<PluginReleaseView> direct(@PathVariable Long id, @Valid @RequestBody PluginReviewRequest request, HttpServletRequest http) {
        return ok("response.success.updated", app.review(id, request, true, true, actor(http), AuditRequestContext.from(http)));
    }
    @PostMapping("/releases/{id}/publish-hosted")
    @Operation(summary = "Retry or execute the hosted channel without claiming external Git or npm success")
    public ApiResponse<PluginReleaseView> hosted(@PathVariable Long id, HttpServletRequest http) {
        return ok("response.success.updated", app.publishHosted(id, actor(http), AuditRequestContext.from(http)));
    }
    @GetMapping("/releases/{id}/download")
    @Operation(summary = "Download an authorized published historical plugin archive")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id,
            @RequestParam(defaultValue = "claude-code") String runtime, HttpServletRequest http) {
        return file(app.openArchive(id, runtime, false, actor(http), AuditRequestContext.from(http)));
    }
    @GetMapping("/releases/{id}/archive")
    @Operation(summary = "Inspect a real archive as project maintainer or platform reviewer")
    public ResponseEntity<InputStreamResource> inspect(@PathVariable Long id,
            @RequestParam(defaultValue = "claude-code") String runtime, HttpServletRequest http) {
        return file(app.openArchive(id, runtime, true, actor(http), AuditRequestContext.from(http)));
    }

    private ResponseEntity<InputStreamResource> file(PluginManagementAppService.Download download) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-SHA256", download.sha256()).body(new InputStreamResource(download.stream()));
    }

    private static PluginActor actor(HttpServletRequest request) {
        Map<Long, NamespaceRole> namespaces = new HashMap<>();
        if (request.getAttribute("userNsRoles") instanceof Map<?, ?> roles) {
            roles.forEach((key, value) -> { if (key instanceof Long id && value instanceof NamespaceRole role) namespaces.put(id, role); });
        }
        Set<String> platform = new HashSet<>();
        if (request.getAttribute("platformRoles") instanceof Set<?> roles) {
            roles.forEach(role -> { if (role instanceof String code) platform.add(code); });
        }
        return new PluginActor(request.getAttribute("userId") instanceof String id ? id : null, namespaces, platform);
    }
}
