package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.catalog.CapabilityCatalogItem;
import com.iflytek.skillhub.service.CapabilityCatalogAppService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plugins")
public class PluginCatalogController extends BaseApiController {
    private final CapabilityCatalogAppService catalogAppService;

    public PluginCatalogController(CapabilityCatalogAppService catalogAppService, ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.catalogAppService = catalogAppService;
    }

    @GetMapping
    @Operation(summary = "List visible published plugins")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Plugin catalog page")
    public ApiResponse<PageResponse<CapabilityCatalogItem>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            @RequestAttribute(value = "platformRoles", required = false) Set<String> platformRoles) {
        return ok("response.success.read",
                catalogAppService.listPlugins(q, page, size, userId, namespaceRoles, platformRoles));
    }

    @GetMapping("/{namespace}/{slug}")
    @Operation(summary = "Get a visible published plugin")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Plugin catalog detail")
    public ApiResponse<CapabilityCatalogItem> detail(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> namespaceRoles,
            @RequestAttribute(value = "platformRoles", required = false) Set<String> platformRoles) {
        return ok("response.success.read",
                catalogAppService.getPlugin(namespace, slug, userId, namespaceRoles, platformRoles));
    }
}
