package com.iflytek.skillhub.dto.software;

/** Secret returned once; list and audit projections never contain it. */
public record SoftwarePublisherCreatedResponse(SoftwarePublisherResponse publisher, String rawToken) { }
