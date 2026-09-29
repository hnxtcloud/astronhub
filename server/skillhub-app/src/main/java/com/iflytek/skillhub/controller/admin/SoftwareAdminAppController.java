package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.software.SoftwareApplication;
import com.iflytek.skillhub.domain.software.SoftwareApplicationStatus;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppAdminSummaryResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppCreateRequest;
import com.iflytek.skillhub.dto.software.SoftwareAppDetailResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppSummaryResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppUpdateRequest;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.software.SoftwareCategoryCreateRequest;
import com.iflytek.skillhub.dto.software.SoftwareCategoryResponse;
import com.iflytek.skillhub.dto.software.SoftwareCategoryUpdateRequest;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.repository.SoftwareCatalogQueryRepository;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SoftwareAdminAppService;
import com.iflytek.skillhub.service.SoftwareCatalogAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for software applications and the category dictionary. The
 * SOFTWARE_ADMIN duty is additive; SUPER_ADMIN always passes.
 */
@RestController
@RequestMapping("/api/v1/admin/software")
public class SoftwareAdminAppController extends BaseApiController {

    private final SoftwareAdminAppService adminAppService;
    private final SoftwareCatalogAppService catalogAppService;
    private final SoftwareCatalogQueryRepository catalogQueryRepository;

    public SoftwareAdminAppController(SoftwareAdminAppService adminAppService,
                                      SoftwareCatalogAppService catalogAppService,
                                      SoftwareCatalogQueryRepository catalogQueryRepository,
                                      ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.adminAppService = adminAppService;
        this.catalogAppService = catalogAppService;
        this.catalogQueryRepository = catalogQueryRepository;
    }

    @GetMapping("/apps")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<SoftwareAppAdminSummaryResponse>> listApps(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SoftwareApplicationStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = SoftwareApplicationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new DomainBadRequestException("error.software.app.statusInvalid", status);
            }
        }
        Page<SoftwareAppAdminSummaryResponse> result = catalogQueryRepository.searchAppsForAdmin(
                keyword, category, statusFilter,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "updatedAt")));
        return ok("response.success.read", new PageResponse<>(
                result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @PostMapping("/apps")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareAppSummaryResponse> createApp(
            @Valid @RequestBody SoftwareAppCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwareApplication app = adminAppService.createApp(request, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.created", toSummary(app));
    }

    @PutMapping("/apps/{id}")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareAppSummaryResponse> updateApp(
            @PathVariable Long id,
            @Valid @RequestBody SoftwareAppUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwareApplication app = adminAppService.updateApp(id, request, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", toSummary(app));
    }

    @PostMapping("/apps/{id}/publish")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareAppSummaryResponse> publishApp(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwareApplication app = adminAppService.publishApp(id, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.published", toSummary(app));
    }

    @PostMapping("/apps/{id}/takedown")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareAppSummaryResponse> takeDownApp(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwareApplication app = adminAppService.takeDownApp(id, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", toSummary(app));
    }

    @PostMapping("/apps/{id}/restore")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareAppSummaryResponse> restoreApp(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        SoftwareApplication app = adminAppService.restoreApp(id, principal.userId(),
                AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", toSummary(app));
    }

    @GetMapping("/apps/{slug}/detail")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareAppDetailResponse> getAppDetail(@PathVariable String slug) {
        SoftwareAppDetailResponse detail = catalogAppService.getAppDetail(slug);
        if (detail == null) {
            throw new com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException(
                    "error.software.app.notFound", slug);
        }
        return ok("response.success.read", detail);
    }

    // ---- Categories ---------------------------------------------------

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<SoftwareCategoryResponse>> listCategories() {
        return ok("response.success.read", adminAppService.listCategories());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareCategoryResponse> createCategory(
            @Valid @RequestBody SoftwareCategoryCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.created", adminAppService.createCategory(request, principal.userId(),
                AuditRequestContext.from(httpRequest)));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareCategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody SoftwareCategoryUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", adminAppService.updateCategory(id, request, principal.userId(),
                AuditRequestContext.from(httpRequest)));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<MessageResponse> deleteCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        adminAppService.deleteCategory(id, principal.userId(), AuditRequestContext.from(httpRequest));
        return ok("response.success.deleted", new MessageResponse("Category deleted"));
    }

    private SoftwareAppSummaryResponse toSummary(SoftwareApplication app) {
        return new SoftwareAppSummaryResponse(app.getId(), app.getSlug(), app.getName(),
                app.getDescription(), app.getIconObjectKey(), app.getCategoryCode(), null,
                app.getCreatedAt());
    }
}
