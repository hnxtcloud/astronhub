package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.mcp.service.McpActor;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpEntryCreateRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpEntryUpdateRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpEntryView;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpGovernanceRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpRejectRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpReviewRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpRevisionCreateRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpRevisionUpdateRequest;
import com.iflytek.skillhub.dto.mcp.McpManagementDtos.McpRevisionView;
import com.iflytek.skillhub.repository.McpManagementQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.mcp.McpManagementAppService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

/** Session-authenticated MCP catalog publication transport. Connection secrets are never request fields. */
@RestController
@RequestMapping("/api/v1/mcp-management")
public class McpManagementController extends BaseApiController {
    private final McpManagementAppService app;
    private final McpManagementQueryRepository queries;

    public McpManagementController(ApiResponseFactory responses, McpManagementAppService app,
                                   McpManagementQueryRepository queries) {
        super(responses);
        this.app = app;
        this.queries = queries;
    }

    @GetMapping("/entries")
    @Operation(summary = "List manageable MCP entries or the review queue")
    public ApiResponse<PageResponse<McpEntryView>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean pendingReview,
            HttpServletRequest http) {
        return ok("response.success.read", queries.list(actor(http), page, size, pendingReview));
    }

    @PostMapping("/entries")
    @Operation(summary = "Create an MCP entry with its first draft revision and connection templates")
    public ApiResponse<McpEntryView> create(
            @Valid @RequestBody McpEntryCreateRequest request, HttpServletRequest http) {
        return ok("response.success.created", app.createEntry(request, actor(http), AuditRequestContext.from(http)));
    }

    @GetMapping("/entries/{id}")
    @Operation(summary = "Read an MCP entry with its revisions, templates, and review evidence")
    public ApiResponse<McpEntryView> detail(@PathVariable Long id, HttpServletRequest http) {
        return ok("response.success.read", queries.detail(id, actor(http)));
    }

    @PatchMapping("/entries/{id}")
    @Operation(summary = "Edit MCP entry catalog metadata")
    public ApiResponse<McpEntryView> update(
            @PathVariable Long id, @Valid @RequestBody McpEntryUpdateRequest request, HttpServletRequest http) {
        return ok("response.success.updated", app.updateEntry(id, request, actor(http), AuditRequestContext.from(http)));
    }

    @PostMapping("/entries/{id}/revisions")
    @Operation(summary = "Create the next draft revision; a published revision stays visible until approval")
    public ApiResponse<McpRevisionView> createRevision(
            @PathVariable Long id, @Valid @RequestBody McpRevisionCreateRequest request, HttpServletRequest http) {
        return ok("response.success.created",
                app.createRevision(id, request, actor(http), AuditRequestContext.from(http)));
    }

    @GetMapping("/revisions/{id}")
    @Operation(summary = "Read a manageable MCP revision with templates and review decisions")
    public ApiResponse<McpRevisionView> revisionDetail(@PathVariable Long id, HttpServletRequest http) {
        return ok("response.success.read", queries.revision(id, actor(http)));
    }

    @PutMapping("/revisions/{id}")
    @Operation(summary = "Edit a draft revision's description, templates, and local curation")
    public ApiResponse<McpRevisionView> updateRevision(
            @PathVariable Long id, @Valid @RequestBody McpRevisionUpdateRequest request, HttpServletRequest http) {
        return ok("response.success.updated",
                app.updateRevision(id, request, actor(http), AuditRequestContext.from(http)));
    }

    @DeleteMapping("/revisions/{id}")
    @Operation(summary = "Delete a draft or rejected revision")
    public ApiResponse<McpRevisionView> deleteRevision(@PathVariable Long id, HttpServletRequest http) {
        return ok("response.success.updated",
                app.deleteRevision(id, actor(http), AuditRequestContext.from(http)));
    }

    @PostMapping("/revisions/{id}/submit")
    @Operation(summary = "Submit a draft revision for review")
    public ApiResponse<McpRevisionView> submit(@PathVariable Long id, HttpServletRequest http) {
        return ok("response.success.updated", app.submit(id, actor(http), AuditRequestContext.from(http)));
    }

    @PostMapping("/revisions/{id}/withdraw")
    @Operation(summary = "Withdraw a pending revision back to draft")
    public ApiResponse<McpRevisionView> withdraw(@PathVariable Long id, HttpServletRequest http) {
        return ok("response.success.updated", app.withdraw(id, actor(http), AuditRequestContext.from(http)));
    }

    @PostMapping("/revisions/{id}/approve")
    @Operation(summary = "Approve a pending revision and publish it as the entry's latest")
    public ApiResponse<McpRevisionView> approve(
            @PathVariable Long id, @Valid @RequestBody McpReviewRequest request, HttpServletRequest http) {
        return ok("response.success.updated", app.approve(id, request, actor(http), AuditRequestContext.from(http)));
    }

    @PostMapping("/revisions/{id}/reject")
    @Operation(summary = "Reject a pending revision with review notes")
    public ApiResponse<McpRevisionView> reject(
            @PathVariable Long id, @Valid @RequestBody McpRejectRequest request, HttpServletRequest http) {
        return ok("response.success.updated", app.reject(id, request, actor(http), AuditRequestContext.from(http)));
    }

    @PostMapping("/revisions/{id}/direct-publish")
    @Operation(summary = "Super-admin direct publication of a validated draft revision")
    public ApiResponse<McpRevisionView> directPublish(
            @PathVariable Long id, @Valid @RequestBody McpReviewRequest request, HttpServletRequest http) {
        return ok("response.success.updated",
                app.directPublish(id, request, actor(http), AuditRequestContext.from(http)));
    }

    @PostMapping("/entries/{id}/governance")
    @Operation(summary = "Govern an MCP entry: HIDE, SHOW, ARCHIVE, RESTORE, or VISIBILITY")
    public ApiResponse<McpEntryView> govern(
            @PathVariable Long id, @Valid @RequestBody McpGovernanceRequest request, HttpServletRequest http) {
        return ok("response.success.updated", app.govern(id, request, actor(http), AuditRequestContext.from(http)));
    }

    static McpActor actor(HttpServletRequest request) {
        Map<Long, NamespaceRole> namespaces = new HashMap<>();
        if (request.getAttribute("userNsRoles") instanceof Map<?, ?> roles) {
            roles.forEach((key, value) -> {
                if (key instanceof Long id && value instanceof NamespaceRole role) namespaces.put(id, role);
            });
        }
        Set<String> platform = new HashSet<>();
        if (request.getAttribute("platformRoles") instanceof Set<?> roles) {
            roles.forEach(role -> {
                if (role instanceof String code) platform.add(code);
            });
        }
        return new McpActor(request.getAttribute("userId") instanceof String id ? id : null, namespaces, platform);
    }
}
