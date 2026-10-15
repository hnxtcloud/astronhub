package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.support.SoftwareUpdateAssetResponder;
import com.iflytek.skillhub.repository.SoftwareUpdateReadQueryRepository;
import com.iflytek.skillhub.service.SoftwareNativeUpdateFeedService;
import com.iflytek.skillhub.dto.software.SoftwareBaizeUpdateManifest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

/** Native update sources use their original bare protocol response, outside the API envelope. */
@RestController
public class SoftwareNativeUpdateFeedController {
    private final SoftwareUpdateReadQueryRepository reads;
    private final SoftwareNativeUpdateFeedService feeds;
    private final SoftwareUpdateAssetResponder responder;

    public SoftwareNativeUpdateFeedController(SoftwareUpdateReadQueryRepository reads,
            SoftwareNativeUpdateFeedService feeds, SoftwareUpdateAssetResponder responder) {
        this.reads = reads;
        this.feeds = feeds;
        this.responder = responder;
    }

    @RequestMapping(value = "/updates/{application}/{channel}/{metadataName}", method = {RequestMethod.GET, RequestMethod.HEAD})
    @Operation(summary = "Read the selected Generic or Baize update feed",
            description = "The configured channel slug fixes platform, architecture, track and variant. Native URL fields point to governed immutable assets, including the deployment prefix. Source and governance are checked before conditional reads.")
    @ApiResponse(responseCode = "200", content = {
            @Content(mediaType = "application/yaml", schema = @Schema(type = "string")),
            @Content(mediaType = "application/json", schema = @Schema(implementation = SoftwareBaizeUpdateManifest.class))})
    public void feed(@PathVariable String application, @PathVariable String channel, @PathVariable String metadataName,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        var target = reads.feed(application, channel, metadataName);
        responder.respondProjection(target, feeds.project(target, application, request.getContextPath()),
                metadataName.endsWith(".json") ? "application/json" : "application/yaml", request, response);
    }
}
