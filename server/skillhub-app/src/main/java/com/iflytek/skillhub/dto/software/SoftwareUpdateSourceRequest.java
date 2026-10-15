package com.iflytek.skillhub.dto.software;
import jakarta.validation.constraints.NotNull;
public record SoftwareUpdateSourceRequest(@NotNull Boolean anonymousEnabled) { }
