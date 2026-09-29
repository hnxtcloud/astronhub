package com.iflytek.skillhub.dto.software;

import com.iflytek.skillhub.domain.software.SoftwarePackageFormat;
import com.iflytek.skillhub.domain.software.SoftwareTargetArch;
import com.iflytek.skillhub.domain.software.SoftwareTargetOs;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One recommendation binding submitted by an administrator. */
public record SoftwareRecommendationItemRequest(
        @NotNull Long packageRevisionId,
        @NotNull SoftwareTargetOs targetOs,
        @NotNull SoftwareTargetArch targetArch,
        @Size(max = 64) String targetDistro,
        @NotNull SoftwarePackageFormat format,
        @Size(max = 512) String note
) {
}
