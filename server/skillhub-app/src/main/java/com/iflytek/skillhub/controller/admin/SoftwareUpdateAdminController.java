package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.software.SoftwareUpdateRules;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.software.*;
import com.iflytek.skillhub.service.SoftwareUpdateAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Session-only software update management; anonymous reads use a separate surface. */
@RestController
@RequestMapping("/api/v1/admin/software")
@PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
public class SoftwareUpdateAdminController extends BaseApiController {
    private final SoftwareUpdateAppService updates;
    public SoftwareUpdateAdminController(SoftwareUpdateAppService updates, ApiResponseFactory responses) {
        super(responses); this.updates = updates;
    }
    @GetMapping("/update-releases")
    public ApiResponse<List<SoftwareUpdateReleaseResponse>> list(
            @RequestParam(required = false) Long catalogReleaseId, @RequestParam(required = false) Long applicationId) {
        if ((catalogReleaseId == null) == (applicationId == null)) SoftwareUpdateRules.invalid("choose catalogReleaseId or applicationId");
        return ok("response.success.read", catalogReleaseId != null ? updates.list(catalogReleaseId) : updates.listPublished(applicationId));
    }
    @PostMapping("/update-releases")
    public ApiResponse<SoftwareUpdateReleaseResponse> create(@Valid @RequestBody SoftwareUpdateCreateRequest body,
            @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.created", updates.create(body, actor.userId()));
    }
    @GetMapping("/update-releases/{id}")
    public ApiResponse<SoftwareUpdateReleaseResponse> get(@PathVariable Long id) {
        return ok("response.success.read", updates.get(id));
    }
    @PostMapping("/update-releases/{id}/package-assets")
    public ApiResponse<SoftwareReleaseAssetResponse> link(@PathVariable Long id,
            @Valid @RequestBody SoftwareUpdatePackageLinkRequest body, @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.created", updates.linkPackage(id, body.packageRevisionId(), body.relativePath(), actor.userId()));
    }
    @PostMapping(value = "/update-releases/{id}/assets", consumes = "application/octet-stream")
    public ApiResponse<SoftwareReleaseAssetResponse> upload(@PathVariable Long id, @RequestParam String path,
            @RequestParam String role, HttpServletRequest request, @AuthenticationPrincipal PlatformPrincipal actor) throws IOException {
        try (var body = request.getInputStream()) {
            return ok("response.success.created", updates.uploadAsset(id, path, role, request.getContentLengthLong(), body, actor.userId()));
        }
    }
    @PostMapping("/update-releases/{id}/validate")
    public ApiResponse<SoftwareUpdateValidationResponse> validate(@PathVariable Long id) {
        updates.validate(id);
        return ok("response.success.read", new SoftwareUpdateValidationResponse(true));
    }
    @DeleteMapping("/update-releases/{id}/assets/{assetId}")
    public ApiResponse<Void> removeAsset(@PathVariable Long id, @PathVariable Long assetId,
            @AuthenticationPrincipal PlatformPrincipal actor) {
        updates.removeAsset(id, assetId, actor.userId());
        return ok("response.success.updated", null);
    }
    @PostMapping("/update-releases/{id}/publish")
    public ApiResponse<SoftwareUpdateReleaseResponse> publish(@PathVariable Long id, @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.published", updates.publish(id, actor.userId()));
    }
    @PostMapping("/update-releases/{id}/withdraw")
    public ApiResponse<SoftwareUpdateReleaseResponse> withdraw(@PathVariable Long id, @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.updated", updates.withdraw(id, actor.userId()));
    }
    @GetMapping("/apps/{applicationId}/update-source")
    public ApiResponse<SoftwareUpdateSourceResponse> source(@PathVariable Long applicationId) {
        return ok("response.success.read", updates.source(applicationId));
    }
    @PutMapping("/apps/{applicationId}/update-source")
    public ApiResponse<SoftwareUpdateSourceResponse> source(@PathVariable Long applicationId,
            @Valid @RequestBody SoftwareUpdateSourceRequest body, @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.updated", updates.setAnonymous(applicationId, body.anonymousEnabled(), actor.userId()));
    }
    @PutMapping("/apps/{applicationId}/update-channels")
    public ApiResponse<SoftwareUpdateChannelResponse> channel(@PathVariable Long applicationId,
            @Valid @RequestBody SoftwareUpdateChannelRequest body, @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.updated", updates.saveChannel(applicationId, body, actor.userId()));
    }
}
