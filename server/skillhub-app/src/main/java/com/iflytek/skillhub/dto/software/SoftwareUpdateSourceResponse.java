package com.iflytek.skillhub.dto.software;
import java.util.List;
public record SoftwareUpdateSourceResponse(Long applicationId, boolean anonymousEnabled, List<SoftwareUpdateChannelResponse> channels) { }
