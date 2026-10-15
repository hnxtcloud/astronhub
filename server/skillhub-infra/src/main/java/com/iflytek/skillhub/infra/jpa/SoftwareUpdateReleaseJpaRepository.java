package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwareUpdateRelease;
import com.iflytek.skillhub.domain.software.SoftwareUpdateReleaseRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SoftwareUpdateReleaseJpaRepository extends JpaRepository<SoftwareUpdateRelease, Long>, SoftwareUpdateReleaseRepository {
    /** Native FOR UPDATE also works on the H2 PostgreSQL-mode acceptance database. */
    @Override
    @Query(value = "SELECT * FROM software_update_release WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<SoftwareUpdateRelease> findLockedById(Long id);
}
