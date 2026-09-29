package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.software.SoftwareAdminGrantRequest;
import com.iflytek.skillhub.dto.software.SoftwareAdminGrantResponse;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SoftwareAdminGrantAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SUPER_ADMIN-only API for granting and revoking the SOFTWARE_ADMIN duty.
 * Grants are additive and never touch the grantee's other roles.
 */
@RestController
@RequestMapping("/api/v1/admin/software/admin-grants")
public class SoftwareAdminGrantController extends BaseApiController {

    private final SoftwareAdminGrantAppService grantAppService;

    public SoftwareAdminGrantController(SoftwareAdminGrantAppService grantAppService,
                                        ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.grantAppService = grantAppService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<SoftwareAdminGrantResponse>> listGrants() {
        return ok("response.success.read", grantAppService.listGrants());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SoftwareAdminGrantResponse> grant(
            @Valid @RequestBody SoftwareAdminGrantRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.created", grantAppService.grant(request.userId(), principal.userId(),
                AuditRequestContext.from(httpRequest)));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<MessageResponse> revoke(
            @PathVariable String userId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        grantAppService.revoke(userId, principal.userId(), AuditRequestContext.from(httpRequest));
        return ok("response.success.revoked", new MessageResponse("SOFTWARE_ADMIN revoked"));
    }
}
