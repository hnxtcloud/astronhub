package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.domain.software.SoftwareRelease;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.software.SoftwarePackageNoteRequest;
import com.iflytek.skillhub.dto.software.SoftwarePackageResponse;
import com.iflytek.skillhub.dto.software.SoftwareRecommendationItemRequest;
import com.iflytek.skillhub.dto.software.SoftwareRecommendationResponse;
import com.iflytek.skillhub.dto.software.SoftwareRecommendationSetRequest;
import com.iflytek.skillhub.dto.software.SoftwareReleaseCreateRequest;
import com.iflytek.skillhub.dto.software.SoftwareReleaseResponse;
import com.iflytek.skillhub.dto.software.SoftwareReleaseUpdateRequest;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SoftwareAdminAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for releases, package revisions, and recommendations. Package
 * creation happens through the upload flow; these endpoints govern metadata
 * confirmation and the publish/takedown lifecycle.
 */
@RestController
@RequestMapping("/api/v1/admin/software")
public class SoftwareAdminReleaseController extends BaseApiController {

    private final SoftwareAdminAppService adminAppService;

    public SoftwareAdminReleaseController(SoftwareAdminAppService adminAppService,
                                          ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.adminAppService = adminAppService;
    }

    // ---- Releases -----------------------------------------------------

    @PostMapping("/apps/{appId}/releases")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareReleaseResponse> createRelease(
            @PathVariable Long appId,
            @Valid @RequestBody SoftwareReleaseCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwareRelease release = adminAppService.createRelease(appId, request, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.created", toRelease(release));
    }

    @GetMapping("/apps/{appId}/releases")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<SoftwareReleaseResponse>> listReleases(@PathVariable Long appId) {
        return ok("response.success.read", adminAppService.listReleases(appId).stream()
                .map(release -> toRelease(release, adminAppService.listPackages(release.getId())))
                .toList());
    }

    @PutMapping("/releases/{id}")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareReleaseResponse> updateRelease(
            @PathVariable Long id,
            @Valid @RequestBody SoftwareReleaseUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwareRelease release = adminAppService.updateRelease(id, request, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", toRelease(release));
    }

    // ---- Packages -----------------------------------------------------

    @GetMapping("/releases/{releaseId}/packages")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<SoftwarePackageResponse>> listPackages(@PathVariable Long releaseId) {
        return ok("response.success.read", adminAppService.listPackages(releaseId).stream()
                .map(this::toPackage)
                .toList());
    }

    @PostMapping("/packages/{id}/confirm")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwarePackageResponse> confirmPackage(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwarePackageRevision revision = adminAppService.confirmPackage(id, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", toPackage(revision));
    }

    @PutMapping("/packages/{id}/note")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwarePackageResponse> updatePackageNote(
            @PathVariable Long id,
            @Valid @RequestBody SoftwarePackageNoteRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwarePackageRevision revision = adminAppService.updatePackageNote(id, request, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", toPackage(revision));
    }

    @PostMapping("/packages/{id}/publish")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwarePackageResponse> publishPackage(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwarePackageRevision revision = adminAppService.publishPackage(id, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.published", toPackage(revision));
    }

    @PostMapping("/packages/{id}/takedown")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwarePackageResponse> takeDownPackage(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwarePackageRevision revision = adminAppService.takeDownPackage(id, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", toPackage(revision));
    }

    @PostMapping("/packages/{id}/restore")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwarePackageResponse> restorePackage(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwarePackageRevision revision = adminAppService.restorePackage(id, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", toPackage(revision));
    }

    // ---- Recommendations ----------------------------------------------

    @PutMapping("/releases/{releaseId}/recommendations")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<SoftwareRecommendationResponse>> setRecommendations(
            @PathVariable Long releaseId,
            @Valid @RequestBody SoftwareRecommendationSetRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        List<com.iflytek.skillhub.domain.software.SoftwareRecommendation> saved =
                adminAppService.setRecommendations(releaseId, request.recommendations(), principal.userId(),
                        AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", saved.stream()
                .map(this::toRecommendation)
                .toList());
    }

    @GetMapping("/releases/{releaseId}/recommendations")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<SoftwareRecommendationResponse>> listRecommendations(
            @PathVariable Long releaseId) {
        return ok("response.success.read", adminAppService.listRecommendations(releaseId));
    }

    private SoftwareReleaseResponse toRelease(SoftwareRelease release) {
        // Single-release paths (create/update) return before the UI refetches the
        // list; the list endpoint assembles packages via toRelease(release, packages).
        return toRelease(release, List.of());
    }

    private SoftwareReleaseResponse toRelease(SoftwareRelease release, List<SoftwarePackageRevision> packages) {
        return new SoftwareReleaseResponse(release.getId(), release.getDisplayVersion(),
                release.getReleaseNotes(), null, packages.size(),
                packages.stream().map(this::toPackage).toList(), List.of());
    }

    private SoftwarePackageResponse toPackage(SoftwarePackageRevision revision) {
        return new SoftwarePackageResponse(revision.getId(), revision.getRevisionNo(),
                revision.getStatus().name(), revision.getTargetOs().name(), revision.getTargetArch().name(),
                revision.getTargetDistro(), revision.getFormat().name(),
                revision.getOriginalFullVersion(), revision.getFileName(), revision.getByteSize(),
                revision.getSha256(), revision.getApplicabilityNote(), revision.getConfirmedBy(),
                revision.getConfirmedAt(), revision.getPublishedAt());
    }

    private SoftwareRecommendationResponse toRecommendation(
            com.iflytek.skillhub.domain.software.SoftwareRecommendation recommendation) {
        return new SoftwareRecommendationResponse(recommendation.getId(),
                recommendation.getPackageRevisionId(), recommendation.getTargetOs().name(),
                recommendation.getTargetArch().name(), recommendation.getTargetDistro(),
                recommendation.getFormat().name(), recommendation.getNote(),
                recommendation.getCreatedAt());
    }
}
