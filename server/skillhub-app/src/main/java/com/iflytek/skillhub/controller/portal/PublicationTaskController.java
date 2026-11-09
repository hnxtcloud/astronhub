package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.*;
import com.iflytek.skillhub.dto.publication.PublicationTaskView;
import com.iflytek.skillhub.dto.publication.PublicationSaveRequest;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.iflytek.skillhub.service.AuditRequestContext;
import org.springframework.web.multipart.MultipartFile;
import com.iflytek.skillhub.dto.publication.PublicationUploadRequest;
import com.iflytek.skillhub.service.publication.PublicationUploadAppService;
import com.iflytek.skillhub.service.publication.PublicationTaskAppService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/web/publications")
public class PublicationTaskController extends BaseApiController {
    private final PublicationTaskAppService app;
    private final PublicationUploadAppService uploads;
    public PublicationTaskController(ApiResponseFactory responses, PublicationTaskAppService app, PublicationUploadAppService uploads) {
        super(responses); this.app = app; this.uploads = uploads;
    }
    @PostMapping
    public ApiResponse<PublicationTaskView> create(@AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.created", app.create(actor.userId()));
    }
    @GetMapping("/{id}")
    public ApiResponse<PublicationTaskView> get(@PathVariable String id, @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.read", app.get(id, actor.userId()));
    }
    @GetMapping
    public ApiResponse<PageResponse<PublicationTaskView>> list(@RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.read", app.list(actor.userId(), page));
    }
    @PutMapping("/{id}")
    public ApiResponse<PublicationTaskView> save(@PathVariable String id, @Valid @RequestBody PublicationSaveRequest request,
            @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.updated", app.save(id, actor.userId(), request));
    }
    @PostMapping("/{id}/cancel")
    public ApiResponse<PublicationTaskView> cancel(@PathVariable String id, @RequestParam long revision,
            @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.updated", app.cancel(id, actor.userId(), revision));
    }
    @PostMapping(value = "/{id}/material", consumes = "multipart/form-data")
    public ApiResponse<PublicationTaskView> material(@PathVariable String id, @RequestParam long revision,
            @RequestParam MultipartFile file, @AuthenticationPrincipal PlatformPrincipal actor) throws java.io.IOException {
        return ok("response.success.updated", app.material(id, actor.userId(), revision, file));
    }
    @PostMapping("/{id}/execute")
    public ApiResponse<PublicationTaskView> execute(@PathVariable String id, @RequestParam long revision,
            @AuthenticationPrincipal PlatformPrincipal actor, HttpServletRequest request) {
        return ok("response.success.updated", app.execute(id, revision, actor, AuditRequestContext.from(request)));
    }
    @PostMapping("/{id}/preview")
    public ApiResponse<PublicationTaskView> preview(@PathVariable String id, @RequestParam long revision,
            @AuthenticationPrincipal PlatformPrincipal actor, HttpServletRequest request) {
        return ok("response.success.updated", app.preview(id, revision, actor, AuditRequestContext.from(request)));
    }
    @GetMapping("/content")
    public ApiResponse<PageResponse<com.iflytek.skillhub.dto.publication.PublicationContentView>> content(
            @RequestParam(defaultValue = "ALL") String type, @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.read", app.content(actor, type, page));
    }
    @PostMapping("/{id}/material-upload")
    public ApiResponse<PublicationTaskView> beginUpload(@PathVariable String id, @RequestParam long revision,
            @Valid @RequestBody PublicationUploadRequest request, @AuthenticationPrincipal PlatformPrincipal actor) {
        uploads.begin(id, revision, request, actor);
        return ok("response.success.updated", app.get(id, actor.userId()));
    }
    @GetMapping("/skill-target")
    public ApiResponse<com.iflytek.skillhub.dto.publication.PublicationContentView> skillTarget(
            @RequestParam String namespace, @RequestParam String slug, @AuthenticationPrincipal PlatformPrincipal actor) {
        return ok("response.success.read", app.skillTarget(actor.userId(), namespace, slug));
    }
    @PutMapping(value = "/{id}/material-upload/{number}", consumes = "application/octet-stream")
    public ApiResponse<PublicationTaskView> uploadChunk(@PathVariable String id, @PathVariable int number,
            @RequestParam long revision, HttpServletRequest request, @AuthenticationPrincipal PlatformPrincipal actor) throws java.io.IOException {
        uploads.chunk(id, revision, number, request.getInputStream(), actor.userId());
        return ok("response.success.updated", app.get(id, actor.userId()));
    }
    @PostMapping("/{id}/material-upload/complete")
    public ApiResponse<PublicationTaskView> completeUpload(@PathVariable String id, @RequestParam long revision,
            @AuthenticationPrincipal PlatformPrincipal actor) {
        uploads.complete(id, revision, actor.userId());
        return ok("response.success.updated", app.get(id, actor.userId()));
    }
}
