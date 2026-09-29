package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.software.SoftwareDownloadSessionResponse;
import com.iflytek.skillhub.service.SoftwareDownloadAppService;
import com.iflytek.skillhub.service.SoftwareDownloadAppService.DownloadTarget;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Site-authenticated download entry. Range, If-Range, HEAD, 200/206/416
 * semantics follow the transfer spec; bytes always stream through the storage
 * layer with a bounded buffer, never a whole-object array. Downloads remain
 * session-scoped: every request re-checks login and publication state.
 */
@RestController
@RequestMapping("/api/v1/software")
public class SoftwareDownloadController extends BaseApiController {

    private static final int STREAM_BUFFER_BYTES = 8 * 1024 * 1024;

    private final SoftwareDownloadAppService downloadAppService;

    public SoftwareDownloadController(SoftwareDownloadAppService downloadAppService,
                                      ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.downloadAppService = downloadAppService;
    }

    @PostMapping("/packages/{packageId}/download-sessions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<SoftwareDownloadSessionResponse> createDownloadSession(
            @PathVariable Long packageId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ok("response.success.created",
                downloadAppService.createSession(packageId, principal.userId()));
    }

    @RequestMapping(value = "/downloads/{sessionId}", method = {org.springframework.web.bind.annotation.RequestMethod.GET,
            org.springframework.web.bind.annotation.RequestMethod.HEAD})
    @PreAuthorize("isAuthenticated()")
    public void download(@PathVariable String sessionId,
                         @RequestHeader(value = "Range", required = false) String rangeHeader,
                         @RequestHeader(value = "If-Range", required = false) String ifRange,
                         @AuthenticationPrincipal PlatformPrincipal principal,
                         HttpServletRequest request,
                         HttpServletResponse response) throws Exception {
        DownloadTarget target = downloadAppService.open(sessionId, principal.userId());
        String etag = "\"" + target.revision().getSha256() + "\"";
        boolean headOnly = "HEAD".equalsIgnoreCase(request.getMethod());
        response.setHeader("ETag", etag);
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + java.net.URLEncoder.encode(target.revision().getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20"));

        SoftwareRangeParser.RangeResult range = SoftwareRangeParser.parse(rangeHeader, target.byteSize());
        if (range instanceof SoftwareRangeParser.RangeResult.Unsatisfiable) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + target.byteSize());
            return;
        }
        // If-Range only honors the range when the object is unchanged; a stale
        // or foreign validator means the client gets the full 200 body.
        boolean ifRangePresent = ifRange != null && !ifRange.isBlank();
        boolean ifRangeMatches = ifRangePresent && ifRange.trim().equals(etag);
        if (!(range instanceof SoftwareRangeParser.RangeResult.Single single)
                || (ifRangePresent && !ifRangeMatches)) {
            serveFullObject(target, response, headOnly);
            return;
        }
        serveRange(target, single, response, headOnly);
    }

    private void serveFullObject(DownloadTarget target, HttpServletResponse response,
                                 boolean headOnly) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentLengthLong(target.byteSize());
        if (headOnly) {
            return;
        }
        try (InputStream in = downloadAppService.openRange(target, 0, target.byteSize());
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    private void serveRange(DownloadTarget target, SoftwareRangeParser.RangeResult.Single single,
                            HttpServletResponse response, boolean headOnly) throws Exception {
        long length = single.end() - single.start() + 1;
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader("Content-Range",
                "bytes " + single.start() + "-" + single.end() + "/" + target.byteSize());
        response.setContentLengthLong(length);
        if (headOnly) {
            return;
        }
        try (InputStream in = downloadAppService.openRange(target, single.start(), length);
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }
}
