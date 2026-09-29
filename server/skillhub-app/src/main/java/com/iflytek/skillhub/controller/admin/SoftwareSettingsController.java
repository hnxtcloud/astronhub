package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.software.SoftwarePolicyRevisionResponse;
import com.iflytek.skillhub.dto.software.SoftwareSettingsResponse;
import com.iflytek.skillhub.dto.software.SoftwareSettingsRollbackRequest;
import com.iflytek.skillhub.dto.software.SoftwareSettingsUpdateRequest;
import com.iflytek.skillhub.dto.software.SoftwareSettingsValidationResponse;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SoftwareSettingsAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Software-center runtime policy settings. Reads (and validation) are open to
 * SOFTWARE_ADMIN and SUPER_ADMIN; writes and rollbacks are SUPER_ADMIN only
 * and always audited. Editing requires the expected active revision, so
 * concurrent edits conflict with 409 instead of last-write-wins.
 */
@RestController
@RequestMapping("/api/v1/admin/software/settings")
public class SoftwareSettingsController extends BaseApiController {

    private final SoftwareSettingsAppService settingsAppService;

    public SoftwareSettingsController(SoftwareSettingsAppService settingsAppService,
                                      ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.settingsAppService = settingsAppService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareSettingsResponse> getActive() {
        SoftwareSettingsResponse settings = settingsAppService.getActive();
        if (settings == null) {
            throw new DomainBadRequestException("error.software.settings.notInitialized");
        }
        return ok("response.success.read", settings);
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SoftwareSettingsResponse> update(
            @Valid @RequestBody SoftwareSettingsUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", settingsAppService.update(
                request.config(), request.expectedRevision(), request.changeReason(),
                principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareSettingsValidationResponse> validate(
            @Valid @RequestBody SoftwareSettingsUpdateRequest request) {
        return ok("response.success.read", settingsAppService.validate(request.config()));
    }

    @PostMapping("/rollback")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SoftwareSettingsResponse> rollback(
            @Valid @RequestBody SoftwareSettingsRollbackRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", settingsAppService.rollback(
                request.fromRevision(), request.changeReason(),
                principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<SoftwarePolicyRevisionResponse>> history(
            @RequestParam(defaultValue = "0") int page) {
        Page<SoftwarePolicyRevisionResponse> history = settingsAppService.history(page);
        return ok("response.success.read", new PageResponse<>(
                history.getContent(), history.getTotalElements(), history.getNumber(), history.getSize()));
    }
}
