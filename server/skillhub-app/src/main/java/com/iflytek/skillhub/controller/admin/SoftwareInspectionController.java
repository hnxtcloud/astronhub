package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.software.SoftwareInspectionTaskStatus;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.software.SoftwareInspectionConfirmMetadataRequest;
import com.iflytek.skillhub.dto.software.SoftwareInspectionDetailResponse;
import com.iflytek.skillhub.dto.software.SoftwareInspectionSummaryResponse;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.SoftwareInspectionAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin inspection console: result queries, controlled retries, and metadata
 * confirmation with manual fill-in. Software admins see every task; a basic
 * validation failure surfaces here and blocks confirmation on the domain side.
 */
@RestController
@RequestMapping("/api/v1/admin/software/inspections")
@PreAuthorize("hasAnyRole('SOFTWARE_ADMIN','SUPER_ADMIN')")
public class SoftwareInspectionController extends BaseApiController {

    private final SoftwareInspectionAppService inspectionAppService;

    public SoftwareInspectionController(SoftwareInspectionAppService inspectionAppService,
                                        ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.inspectionAppService = inspectionAppService;
    }

    @GetMapping
    public ApiResponse<PageResponse<SoftwareInspectionSummaryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        return ok("response.success.retrieved",
                PageResponse.from(inspectionAppService.list(parseStatus(status), pageable)));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<SoftwareInspectionDetailResponse> detail(@PathVariable Long taskId) {
        return ok("response.success.retrieved", inspectionAppService.detail(taskId));
    }

    @PostMapping("/{taskId}/retry")
    public ApiResponse<SoftwareInspectionSummaryResponse> retry(
            @PathVariable Long taskId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        inspectionAppService.retry(taskId, principal.userId(), AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", inspectionAppService.summary(taskId));
    }

    @PostMapping("/{taskId}/confirm-metadata")
    public ApiResponse<SoftwareInspectionSummaryResponse> confirmMetadata(
            @PathVariable Long taskId,
            @Valid @RequestBody SoftwareInspectionConfirmMetadataRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        inspectionAppService.confirmMetadata(
                taskId, request.rawVersionFields(), principal.userId(), AuditRequestContext.from(httpRequest));
        return ok("response.success.updated", inspectionAppService.summary(taskId));
    }

    private SoftwareInspectionTaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SoftwareInspectionTaskStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                    "error.software.inspection.statusInvalid", status);
        }
    }
}
