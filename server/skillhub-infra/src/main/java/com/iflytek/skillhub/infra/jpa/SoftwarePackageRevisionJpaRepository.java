package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevisionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwarePackageRevisionJpaRepository
        extends JpaRepository<SoftwarePackageRevision, Long>, SoftwarePackageRevisionRepository {

    @Override
    List<SoftwarePackageRevision> findByReleaseIdOrderByIdDesc(Long releaseId);

    @Override
    List<SoftwarePackageRevision> findByApplicationIdIn(List<Long> applicationIds);

    @Override
    List<SoftwarePackageRevision> findByApplicationId(Long applicationId);

    @Override
    boolean existsByApplicationIdAndSha256(Long applicationId, String sha256);

    @Override
    long countByApplicationId(Long applicationId);
}
