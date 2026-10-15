package com.iflytek.skillhub.dto.software;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record SoftwareUpdatePackageLinkRequest(@NotNull Long packageRevisionId, @NotBlank String relativePath) { }
