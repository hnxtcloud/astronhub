package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.dto.software.*;
import com.iflytek.skillhub.service.SoftwarePublisherAppService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/software/apps/{appId}/publishers")
@PreAuthorize("hasAnyRole('SOFTWARE_ADMIN','SUPER_ADMIN')")
public class SoftwarePublisherAdminController extends BaseApiController {
    private final SoftwarePublisherAppService publishers;
    public SoftwarePublisherAdminController(SoftwarePublisherAppService publishers, ApiResponseFactory responses) {
        super(responses); this.publishers = publishers;
    }
    @PostMapping
    public ApiResponse<SoftwarePublisherCreatedResponse> create(@PathVariable Long appId,
            @Valid @RequestBody SoftwarePublisherCreateRequest body, Authentication auth, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ok("response.success.created", publishers.create(appId, body, auth));
    }
    @GetMapping
    public ApiResponse<List<SoftwarePublisherResponse>> list(@PathVariable Long appId, Authentication auth) {
        return ok("response.success.read", publishers.list(appId, auth));
    }
    @DeleteMapping("/{tokenId}")
    public ApiResponse<Void> revoke(@PathVariable Long appId, @PathVariable Long tokenId, Authentication auth) {
        publishers.revoke(appId, tokenId, auth); return ok("response.success.updated", null);
    }
}
