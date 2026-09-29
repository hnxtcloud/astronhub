package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.software.SoftwareCategory;
import com.iflytek.skillhub.domain.software.SoftwareCategoryService;
import com.iflytek.skillhub.domain.software.SoftwareTargetArch;
import com.iflytek.skillhub.domain.software.SoftwareTargetOs;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppDetailResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppSummaryResponse;
import com.iflytek.skillhub.dto.software.SoftwareCategoryResponse;
import com.iflytek.skillhub.repository.SoftwareCatalogQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Portal-side software catalog reads. Only published content is ever returned;
 * anonymous access is already rejected by the route security policy.
 */
@Service
public class SoftwareCatalogAppService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SoftwareCatalogQueryRepository catalogQueryRepository;
    private final SoftwareCategoryService categoryService;

    public SoftwareCatalogAppService(SoftwareCatalogQueryRepository catalogQueryRepository,
                                     SoftwareCategoryService categoryService) {
        this.catalogQueryRepository = catalogQueryRepository;
        this.categoryService = categoryService;
    }

    public PageResponse<SoftwareAppSummaryResponse> listApps(String keyword,
                                                             String categoryCode,
                                                             String targetOs,
                                                             String targetArch,
                                                             int page,
                                                             int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<SoftwareAppSummaryResponse> result = catalogQueryRepository.searchPublishedApps(
                StringUtils.hasText(keyword) ? keyword.trim() : null,
                StringUtils.hasText(categoryCode) ? categoryCode.trim() : null,
                parseEnum(targetOs, SoftwareTargetOs.class, "targetOs"),
                parseEnum(targetArch, SoftwareTargetArch.class, "targetArch"),
                pageable
        );
        return new PageResponse<>(result.getContent(), result.getTotalElements(),
                result.getNumber(), result.getSize());
    }

    /** Returns the published app detail or null; callers translate to 404. */
    public SoftwareAppDetailResponse getAppDetail(String slug) {
        return catalogQueryRepository.findPublishedAppDetail(slug);
    }

    public java.util.List<SoftwareCategoryResponse> listCategories() {
        return categoryService.listAll().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    private SoftwareCategoryResponse toCategoryResponse(SoftwareCategory category) {
        return new SoftwareCategoryResponse(category.getId(), category.getCode(), category.getName(),
                category.getSortOrder());
    }

    private <E extends Enum<E>> E parseEnum(String raw, Class<E> type, String field) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException(
                    "error.software.filter.invalid", field, raw);
        }
    }
}
