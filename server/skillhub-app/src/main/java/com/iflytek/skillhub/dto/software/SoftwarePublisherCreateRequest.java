package com.iflytek.skillhub.dto.software;

public record SoftwarePublisherCreateRequest(@jakarta.validation.constraints.NotBlank String name,
        @jakarta.validation.constraints.NotBlank String expiresAt) { }
