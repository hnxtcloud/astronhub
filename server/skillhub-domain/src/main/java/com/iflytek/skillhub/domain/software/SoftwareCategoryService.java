package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Category dictionary rules. Independent from skill labels: categories are
 * software-center local, and deleting a category still referenced by any
 * application is refused — the application must be re-categorized first.
 */
@Service
public class SoftwareCategoryService {

    private static final int MAX_CATEGORIES = 100;

    private final SoftwareCategoryRepository categoryRepository;
    private final SoftwareApplicationRepository applicationRepository;

    public SoftwareCategoryService(SoftwareCategoryRepository categoryRepository,
                                   SoftwareApplicationRepository applicationRepository) {
        this.categoryRepository = categoryRepository;
        this.applicationRepository = applicationRepository;
    }

    public List<SoftwareCategory> listAll() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional
    public SoftwareCategory create(String code, String name, int sortOrder, String actorId) {
        String normalizedCode = normalizeCode(code);
        if (!StringUtils.hasText(name)) {
            throw new DomainBadRequestException("error.software.category.nameRequired");
        }
        if (categoryRepository.countByCode(normalizedCode) > 0) {
            throw new DomainBadRequestException("error.software.category.duplicate", normalizedCode);
        }
        if (categoryRepository.findAllByOrderBySortOrderAscIdAsc().size() >= MAX_CATEGORIES) {
            throw new DomainBadRequestException("error.software.category.tooMany", MAX_CATEGORIES);
        }
        return categoryRepository.save(new SoftwareCategory(normalizedCode, name.trim(), sortOrder, actorId));
    }

    @Transactional
    public SoftwareCategory update(Long categoryId, String name, int sortOrder) {
        SoftwareCategory category = requireCategory(categoryId);
        if (!StringUtils.hasText(name)) {
            throw new DomainBadRequestException("error.software.category.nameRequired");
        }
        category.setName(name.trim());
        category.setSortOrder(sortOrder);
        return categoryRepository.save(category);
    }

    /** Deletes an unreferenced category; referenced ones must be re-categorized first. */
    @Transactional
    public void delete(Long categoryId) {
        SoftwareCategory category = requireCategory(categoryId);
        if (applicationRepository.existsByCategoryCode(category.getCode())) {
            throw new DomainConflictException("error.software.category.inUse", category.getCode());
        }
        categoryRepository.delete(category);
    }

    public SoftwareCategory requireCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DomainNotFoundException("error.software.category.notFound", categoryId));
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new DomainBadRequestException("error.software.category.codeRequired");
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }
}
