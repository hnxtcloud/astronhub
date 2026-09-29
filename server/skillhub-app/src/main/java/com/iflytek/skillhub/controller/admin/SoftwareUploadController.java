package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.software.SoftwareChunkReceiptResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadCompleteResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadProgressResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadSessionCreateRequest;
import com.iflytek.skillhub.dto.software.SoftwareUploadSessionResponse;
import com.iflytek.skillhub.service.SoftwareUploadAppService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin chunked upload API. Every request re-validates the caller's role and
 * the session owner; the object key, chunk boundaries and digests are all
 * decided server-side.
 */
@RestController
@RequestMapping("/api/v1/admin/software/uploads")
public class SoftwareUploadController extends BaseApiController {

    private final SoftwareUploadAppService uploadAppService;

    public SoftwareUploadController(SoftwareUploadAppService uploadAppService,
                                    ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.uploadAppService = uploadAppService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareUploadSessionResponse> createSession(
            @RequestBody SoftwareUploadSessionCreateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.created",
                uploadAppService.createSession(request, principal.userId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareUploadProgressResponse> progress(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.read", uploadAppService.progress(id, principal.userId()));
    }

    /**
     * Accepts one chunk. The digest is computed from the received bytes; the
     * optional {@code X-Chunk-Sha256} header is only a cross-check and never
     * authoritative.
     */
    @PutMapping("/{id}/chunks/{chunkNo}")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareChunkReceiptResponse> putChunk(
            @PathVariable Long id,
            @PathVariable int chunkNo,
            @RequestHeader(value = "Content-Length", required = false) Long contentLength,
            @RequestHeader(value = "X-Chunk-Sha256", required = false) String declaredSha256,
            InputStream body,
            @AuthenticationPrincipal PlatformPrincipal principal) throws IOException {
        if (contentLength == null) {
            throw new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                    "error.software.upload.chunkLengthRequired");
        }
        SoftwareChunkReceiptResponse receipt = uploadAppService.putChunk(
                id, chunkNo, contentLength, body, principal.userId());
        verifyDeclaredDigest(declaredSha256, receipt);
        return ok("response.success.updated", receipt);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareUploadCompleteResponse> complete(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.updated", uploadAppService.complete(id, principal.userId()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SOFTWARE_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<SoftwareUploadSessionResponse> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.updated", uploadAppService.cancel(id, principal.userId()));
    }

    private void verifyDeclaredDigest(String declaredSha256, SoftwareChunkReceiptResponse receipt) {
        if (declaredSha256 == null || declaredSha256.isBlank()) {
            return;
        }
        if (!declaredSha256.trim().equalsIgnoreCase(receipt.sha256())) {
            throw new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                    "error.software.upload.digestMismatch", receipt.chunkNo());
        }
    }
}
