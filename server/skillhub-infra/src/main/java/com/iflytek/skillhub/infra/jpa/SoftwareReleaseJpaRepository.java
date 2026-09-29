package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwareRelease;
import com.iflytek.skillhub.domain.software.SoftwareReleaseRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwareReleaseJpaRepository
        extends JpaRepository<SoftwareRelease, Long>, SoftwareReleaseRepository {

    @Override
    Optional<SoftwareRelease> findByApplicationIdAndDisplayVersion(Long applicationId, String displayVersion);

    @Override
    List<SoftwareRelease> findByApplicationIdOrderByIdDesc(Long applicationId);

    @Override
    long countByApplicationId(Long applicationId);
}
