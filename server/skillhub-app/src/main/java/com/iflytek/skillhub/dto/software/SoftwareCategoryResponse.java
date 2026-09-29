package com.iflytek.skillhub.dto.software;

/** Category dictionary entry of the software center. */
public record SoftwareCategoryResponse(
        Long id,
        String code,
        String name,
        int sortOrder
) {
}
