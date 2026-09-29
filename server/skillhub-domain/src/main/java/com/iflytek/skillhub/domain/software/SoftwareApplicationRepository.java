package com.iflytek.skillhub.domain.software;

import java.util.Optional;

/**
 * Repository port for software applications.
 */
public interface SoftwareApplicationRepository {

    SoftwareApplication save(SoftwareApplication application);

    Optional<SoftwareApplication> findById(Long id);

    Optional<SoftwareApplication> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByCategoryCode(String categoryCode);
}
