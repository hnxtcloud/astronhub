package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppDetailResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppSummaryResponse;
import com.iflytek.skillhub.dto.software.SoftwareCategoryResponse;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.service.SoftwareCatalogAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal software catalog. Login is enforced by the route security policy;
 * only published content is returned.
 */
@RestController
@RequestMapping("/api/v1/software")
@Tag(name = "Software Center", description = "Internal software catalog for logged-in users")
public class SoftwareCatalogController extends BaseApiController {

    private final SoftwareCatalogAppService catalogAppService;

    public SoftwareCatalogController(SoftwareCatalogAppService catalogAppService,
                                     ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.catalogAppService = catalogAppService;
    }

    @GetMapping("/apps")
    @Operation(summary = "List published software applications with keyword, category, and platform filters")
    public ApiResponse<PageResponse<SoftwareAppSummaryResponse>> listApps(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String os,
            @RequestParam(required = false) String arch,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success.read",
                catalogAppService.listApps(keyword, category, os, arch, page, size));
    }

    @GetMapping("/apps/{slug}")
    @Operation(summary = "Get a published application with releases, packages, and recommendations")
    public ApiResponse<SoftwareAppDetailResponse> getAppDetail(@PathVariable String slug) {
        SoftwareAppDetailResponse detail = catalogAppService.getAppDetail(slug);
        if (detail == null) {
            throw new DomainNotFoundException("error.software.app.notFound", slug);
        }
        return ok("response.success.read", detail);
    }

    @GetMapping("/categories")
    @Operation(summary = "List software-center categories for filtering")
    public ApiResponse<java.util.List<SoftwareCategoryResponse>> listCategories() {
        return ok("response.success.read", catalogAppService.listCategories());
    }
}
