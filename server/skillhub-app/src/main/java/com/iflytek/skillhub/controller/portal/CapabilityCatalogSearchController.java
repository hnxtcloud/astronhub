package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.catalog.CapabilityCatalogItem;
import com.iflytek.skillhub.dto.catalog.CapabilityTypeFilter;
import com.iflytek.skillhub.service.CapabilityCatalogAppService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/search")
public class CapabilityCatalogSearchController extends BaseApiController {
    private final CapabilityCatalogAppService catalogAppService;

    public CapabilityCatalogSearchController(
            CapabilityCatalogAppService catalogAppService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.catalogAppService = catalogAppService;
    }

    @GetMapping
    @Operation(summary = "Search the unified capability catalog")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Unified capability catalog page")
    public ApiResponse<PageResponse<CapabilityCatalogItem>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ALL") CapabilityTypeFilter type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            @RequestAttribute(value = "platformRoles", required = false) Set<String> platformRoles) {
        return ok("response.success.read",
                catalogAppService.search(q, type, page, size, userId, namespaceRoles, platformRoles));
    }
}
