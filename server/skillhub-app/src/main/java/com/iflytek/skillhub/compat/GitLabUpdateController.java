package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.auth.software.GitLabUpdatePath;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.repository.SoftwareUpdateReadQueryRepository;
import com.iflytek.skillhub.controller.support.SoftwareUpdateAssetResponder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.*;

/** Minimal GitLab read projection. PRIVATE-TOKEN is deliberately not an authentication input. */
@RestController
public class GitLabUpdateController {
    private final SoftwareUpdateReadQueryRepository reads;
    private final SoftwareUpdateAssetResponder assets;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public GitLabUpdateController(SoftwareUpdateReadQueryRepository reads, SoftwareUpdateAssetResponder assets,
            com.fasterxml.jackson.databind.ObjectMapper json) { this.reads = reads; this.assets = assets; this.json = json; }
    @RequestMapping(value = "/api/v4/projects/**", method = {RequestMethod.GET, RequestMethod.HEAD})
    @Operation(summary = "Read GitLab-compatible update tags or raw assets",
            description = "A validated encoded project path selects one governed channel target. Tags return a bare singleton array; raw files require exactly one ref parameter. HEAD omits the response body.",
            parameters = @Parameter(name = "ref", in = ParameterIn.QUERY, description = "Exact original tag, required only for raw files", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "200", content = {
            @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = SoftwareUpdateReadQueryRepository.GitLabTag.class))),
            @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary"))})
    public void read(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        var route = GitLabUpdatePath.parse(request).orElseThrow(() -> new DomainBadRequestException("error.software.update.invalid", "GitLab route"));
        response.setHeader("Cache-Control", "no-store");
        if (route.tags()) {
            byte[] body = json.writeValueAsBytes(reads.tags(route.project()));
            response.setContentType("application/json"); response.setHeader("Content-Length", Integer.toString(body.length));
            if (!"HEAD".equals(request.getMethod())) response.getOutputStream().write(body);
            return;
        }
        String[] refs = request.getParameterValues("ref");
        if (refs == null || refs.length != 1 || refs[0].isBlank()) throw new DomainBadRequestException("error.software.update.invalid", "one ref required");
        assets.respond(reads.raw(route.project(), refs[0], route.file()), request, response);
    }
    @RequestMapping(value = "/releases/{application}/{releaseId}/assets/**", method = {RequestMethod.GET, RequestMethod.HEAD})
    @Operation(summary = "Read an immutable governed update asset",
            description = "Source opt-in and current application, snapshot and linked-package governance are checked before conditional or range responses. HEAD omits the response body.",
            parameters = {
                    @Parameter(name = "application", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string")),
                    @Parameter(name = "releaseId", in = ParameterIn.PATH, required = true, schema = @Schema(type = "integer", format = "int64"))})
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    public void fixed(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        var match = java.util.regex.Pattern.compile("^/releases/([^/]+)/([0-9]+)/assets/(.+)$").matcher(GitLabUpdatePath.requestPath(request));
        if (!match.matches()) throw invalidRoute();
        try {
            assets.respond(reads.fixed(GitLabUpdatePath.decode(match.group(1)), Long.parseLong(match.group(2)),
                    GitLabUpdatePath.decode(match.group(3))), request, response);
        } catch (IllegalArgumentException invalid) { throw invalidRoute(); }
    }
    @RequestMapping(value = "/{owner}/{repo}/-/raw/{tag}/**", method = {RequestMethod.GET, RequestMethod.HEAD})
    @Operation(summary = "Read a GitLab web-raw update asset",
            description = "The owner/repo virtual project, exact tag and original asset path are resolved within the governed channel. HEAD omits the response body.",
            parameters = {
                    @Parameter(name = "owner", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string")),
                    @Parameter(name = "repo", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string")),
                    @Parameter(name = "tag", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    public void webRaw(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        var match = java.util.regex.Pattern.compile("^/([^/]+)/([^/]+)/-/raw/([^/]+)/(.+)$").matcher(GitLabUpdatePath.requestPath(request));
        if (!match.matches()) throw invalidRoute();
        try {
            assets.respond(reads.raw(GitLabUpdatePath.decode(match.group(1)) + "/" + GitLabUpdatePath.decode(match.group(2)),
                    GitLabUpdatePath.decode(match.group(3)), GitLabUpdatePath.decode(match.group(4))), request, response);
        } catch (IllegalArgumentException invalid) { throw invalidRoute(); }
    }
    private static DomainBadRequestException invalidRoute() { return new DomainBadRequestException("error.software.update.invalid", "update route"); }
}
