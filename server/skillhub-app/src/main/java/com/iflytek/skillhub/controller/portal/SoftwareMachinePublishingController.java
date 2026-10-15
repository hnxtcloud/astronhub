package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.dto.software.*;
import com.iflytek.skillhub.service.SoftwareMachinePublishingAppService;
import com.iflytek.skillhub.service.AuditRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Bearer-only publishing; session administration and update reads are separate surfaces. */
@RestController
@RequestMapping("/api/v1/software-publishing/apps/{appId}")
public class SoftwareMachinePublishingController extends BaseApiController {
    private final SoftwareMachinePublishingAppService publishing;
    public SoftwareMachinePublishingController(SoftwareMachinePublishingAppService publishing, ApiResponseFactory responses) {
        super(responses); this.publishing = publishing;
    }
    @GetMapping("/releases")
    public ApiResponse<List<SoftwareReleaseResponse>> releases(@PathVariable Long appId, Authentication auth) {
        return ok("response.success.read", publishing.releases(appId, auth));
    }
    @PostMapping("/releases")
    public ApiResponse<SoftwareReleaseResponse> createRelease(@PathVariable Long appId, @Valid @RequestBody SoftwareReleaseCreateRequest body,
            Authentication auth, HttpServletRequest request) {
        return ok("response.success.created", publishing.createRelease(appId, body, auth, AuditRequestContext.from(request)));
    }
    @GetMapping("/releases/{id}/packages")
    public ApiResponse<List<SoftwarePackageResponse>> packages(@PathVariable Long appId, @PathVariable Long id, Authentication auth) {
        return ok("response.success.read", publishing.packages(appId, id, auth));
    }
    @PostMapping("/uploads")
    public ApiResponse<SoftwareUploadSessionResponse> createUpload(@PathVariable Long appId, @RequestBody SoftwareUploadSessionCreateRequest body, Authentication auth) {
        return ok("response.success.created", publishing.createUpload(appId, body, auth));
    }
    @GetMapping("/uploads/{id}")
    public ApiResponse<SoftwareUploadProgressResponse> progress(@PathVariable Long appId, @PathVariable Long id, Authentication auth) {
        return ok("response.success.read", publishing.progress(appId, id, auth));
    }
    @PutMapping(value = "/uploads/{id}/chunks/{number}", consumes = "application/octet-stream")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @io.swagger.v3.oas.annotations.media.Content(
            mediaType = "application/octet-stream", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary")))
    public ApiResponse<SoftwareChunkReceiptResponse> chunk(@PathVariable Long appId, @PathVariable Long id, @PathVariable int number,
            @RequestHeader(value = "X-Chunk-Sha256", required = false) String digest, Authentication auth, HttpServletRequest request) throws IOException {
        try (var body = request.getInputStream()) {
            return ok("response.success.updated", publishing.chunk(appId, id, number, request.getContentLengthLong(), body, digest, auth));
        }
    }
    @PostMapping("/uploads/{id}/complete")
    public ApiResponse<SoftwareUploadCompleteResponse> complete(@PathVariable Long appId, @PathVariable Long id, Authentication auth) {
        return ok("response.success.updated", publishing.complete(appId, id, auth));
    }
    @PostMapping("/uploads/{id}/cancel")
    public ApiResponse<SoftwareUploadSessionResponse> cancel(@PathVariable Long appId, @PathVariable Long id, Authentication auth) {
        return ok("response.success.updated", publishing.cancel(appId, id, auth));
    }
    @GetMapping("/packages/{id}")
    public ApiResponse<SoftwareMachinePackageResponse> packageStatus(@PathVariable Long appId, @PathVariable Long id, Authentication auth) {
        return ok("response.success.read", publishing.packageStatus(appId, id, auth));
    }
    @PostMapping("/packages/{id}/confirm")
    public ApiResponse<SoftwarePackageResponse> confirm(@PathVariable Long appId, @PathVariable Long id,
            @RequestBody SoftwareInspectionConfirmMetadataRequest body, Authentication auth, HttpServletRequest request) {
        return ok("response.success.updated", publishing.confirm(appId, id, body.rawVersionFields(), auth, AuditRequestContext.from(request)));
    }
    @PostMapping("/packages/{id}/publish")
    public ApiResponse<SoftwarePackageResponse> publishPackage(@PathVariable Long appId, @PathVariable Long id, Authentication auth, HttpServletRequest request) {
        return ok("response.success.published", publishing.publishPackage(appId, id, auth, AuditRequestContext.from(request)));
    }
    @PostMapping("/packages/{id}/retry-inspection")
    public ApiResponse<SoftwareInspectionDetailResponse> retryInspection(@PathVariable Long appId, @PathVariable Long id,
            Authentication auth, HttpServletRequest request) {
        return ok("response.success.updated", publishing.retryInspection(appId, id, auth, AuditRequestContext.from(request)));
    }
    @PostMapping("/update-releases")
    public ApiResponse<SoftwareUpdateReleaseResponse> createSnapshot(@PathVariable Long appId, @Valid @RequestBody SoftwareUpdateCreateRequest body, Authentication auth) {
        return ok("response.success.created", publishing.createSnapshot(appId, body, auth));
    }
    @GetMapping("/releases/{id}/update-releases")
    public ApiResponse<List<SoftwareUpdateReleaseResponse>> snapshots(@PathVariable Long appId, @PathVariable Long id, Authentication auth) {
        return ok("response.success.read", publishing.snapshots(appId, id, auth));
    }
    @GetMapping("/update-releases/{id}")
    public ApiResponse<SoftwareUpdateReleaseResponse> snapshot(@PathVariable Long appId, @PathVariable Long id, Authentication auth) {
        return ok("response.success.read", publishing.snapshot(appId, id, auth));
    }
    @PostMapping("/update-releases/{id}/package-assets")
    public ApiResponse<SoftwareReleaseAssetResponse> link(@PathVariable Long appId, @PathVariable Long id,
            @Valid @RequestBody SoftwareUpdatePackageLinkRequest body, Authentication auth) {
        return ok("response.success.created", publishing.link(appId, id, body, auth));
    }
    @PostMapping(value = "/update-releases/{id}/assets", consumes = "application/octet-stream")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @io.swagger.v3.oas.annotations.media.Content(
            mediaType = "application/octet-stream", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary")))
    public ApiResponse<SoftwareReleaseAssetResponse> asset(@PathVariable Long appId, @PathVariable Long id,
            @RequestParam String path, @RequestParam String role, Authentication auth, HttpServletRequest request) throws IOException {
        try (var body = request.getInputStream()) {
            return ok("response.success.created", publishing.asset(appId, id, path, role, request.getContentLengthLong(), body, auth));
        }
    }
    @PostMapping("/update-releases/{id}/validate")
    public ApiResponse<SoftwareUpdateValidationResponse> validate(@PathVariable Long appId, @PathVariable Long id, Authentication auth) {
        return ok("response.success.read", publishing.validate(appId, id, auth));
    }
    @PostMapping("/update-releases/{id}/publish")
    public ApiResponse<SoftwareUpdateReleaseResponse> publish(@PathVariable Long appId, @PathVariable Long id, Authentication auth) {
        return ok("response.success.published", publishing.publish(appId, id, auth));
    }
    @PutMapping("/update-channels")
    public ApiResponse<SoftwareUpdateChannelResponse> channel(@PathVariable Long appId, @RequestBody SoftwareUpdateChannelRequest body, Authentication auth) {
        return ok("response.success.updated", publishing.channel(appId, body, auth));
    }
}
