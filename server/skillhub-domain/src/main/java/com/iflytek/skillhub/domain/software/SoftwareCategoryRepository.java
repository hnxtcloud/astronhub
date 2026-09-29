package com.iflytek.skillhub.domain.software;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for the software-center category dictionary.
 */
public interface SoftwareCategoryRepository {

    SoftwareCategory save(SoftwareCategory category);

    Optional<SoftwareCategory> findById(Long id);

    Optional<SoftwareCategory> findByCode(String code);

    List<SoftwareCategory> findAllByOrderBySortOrderAscIdAsc();

    long countByCode(String code);

    void delete(SoftwareCategory category);
}
