package com.iflytek.skillhub.domain.software;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for software releases (display-version groupings).
 */
public interface SoftwareReleaseRepository {

    SoftwareRelease save(SoftwareRelease release);

    Optional<SoftwareRelease> findById(Long id);

    Optional<SoftwareRelease> findByApplicationIdAndDisplayVersion(Long applicationId, String displayVersion);

    List<SoftwareRelease> findByApplicationIdOrderByIdDesc(Long applicationId);

    long countByApplicationId(Long applicationId);
}
