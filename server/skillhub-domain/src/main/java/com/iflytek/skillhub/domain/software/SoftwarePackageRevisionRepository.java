package com.iflytek.skillhub.domain.software;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for installable package revisions.
 */
public interface SoftwarePackageRevisionRepository {

    SoftwarePackageRevision save(SoftwarePackageRevision revision);

    Optional<SoftwarePackageRevision> findById(Long id);

    List<SoftwarePackageRevision> findByReleaseIdOrderByIdDesc(Long releaseId);

    List<SoftwarePackageRevision> findByApplicationIdIn(List<Long> applicationIds);

    List<SoftwarePackageRevision> findByApplicationId(Long applicationId);

    boolean existsByApplicationIdAndSha256(Long applicationId, String sha256);

    long countByApplicationId(Long applicationId);
}
