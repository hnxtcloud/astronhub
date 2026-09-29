package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwareRecommendation;
import com.iflytek.skillhub.domain.software.SoftwareRecommendationRepository;
import com.iflytek.skillhub.domain.software.SoftwarePackageFormat;
import com.iflytek.skillhub.domain.software.SoftwareTargetArch;
import com.iflytek.skillhub.domain.software.SoftwareTargetOs;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwareRecommendationJpaRepository
        extends JpaRepository<SoftwareRecommendation, Long>, SoftwareRecommendationRepository {

    @Override
    List<SoftwareRecommendation> findByReleaseId(Long releaseId);

    @Override
    List<SoftwareRecommendation> findByPackageRevisionId(Long packageRevisionId);

    @Override
    Optional<SoftwareRecommendation> findByReleaseIdAndTargetOsAndTargetArchAndTargetDistroAndFormat(
            Long releaseId, SoftwareTargetOs targetOs, SoftwareTargetArch targetArch,
            String targetDistro, SoftwarePackageFormat format);

    @Override
    default void deleteAllIn(List<SoftwareRecommendation> recommendations) {
        deleteAll(recommendations);
    }

    @Override
    void deleteByPackageRevisionId(Long packageRevisionId);

    @Override
    long countByReleaseId(Long releaseId);
}
