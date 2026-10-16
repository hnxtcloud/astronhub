package com.iflytek.skillhub.dto.software;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record SoftwareUpdatePackageLinkRequest(@NotNull Long packageRevisionId, @NotBlank String relativePath,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Explicit publisher confirmation for Windows Electron NSIS installers; never inferred from EXE format or package metadata")
        @jakarta.validation.constraints.Pattern(regexp = "NSIS") String confirmedInstallerFamily) { }
