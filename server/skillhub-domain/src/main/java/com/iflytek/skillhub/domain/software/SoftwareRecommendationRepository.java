package com.iflytek.skillhub.domain.software;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for download recommendations.
 */
public interface SoftwareRecommendationRepository {

    SoftwareRecommendation save(SoftwareRecommendation recommendation);

    Optional<SoftwareRecommendation> findById(Long id);

    List<SoftwareRecommendation> findByReleaseId(Long releaseId);

    List<SoftwareRecommendation> findByPackageRevisionId(Long packageRevisionId);

    Optional<SoftwareRecommendation> findByReleaseIdAndTargetOsAndTargetArchAndTargetDistroAndFormat(
            Long releaseId,
            SoftwareTargetOs targetOs,
            SoftwareTargetArch targetArch,
            String targetDistro,
            SoftwarePackageFormat format);

    void delete(SoftwareRecommendation recommendation);

    void deleteAllIn(List<SoftwareRecommendation> recommendations);

    void deleteByPackageRevisionId(Long packageRevisionId);

    long countByReleaseId(Long releaseId);
}
