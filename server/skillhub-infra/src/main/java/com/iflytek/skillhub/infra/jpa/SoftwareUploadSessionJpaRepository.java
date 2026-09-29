package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwareUploadSession;
import com.iflytek.skillhub.domain.software.SoftwareUploadSessionRepository;
import com.iflytek.skillhub.domain.software.SoftwareUploadSessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwareUploadSessionJpaRepository
        extends JpaRepository<SoftwareUploadSession, Long>, SoftwareUploadSessionRepository {

    @Override
    long countByOwnerUserIdAndStatus(String ownerUserId, SoftwareUploadSessionStatus status);

    @Query("SELECT s FROM SoftwareUploadSession s WHERE s.status = 'ACTIVE' AND s.expiresAt < :now "
            + "ORDER BY s.expiresAt ASC")
    List<SoftwareUploadSession> findExpired(@Param("now") Instant now, Pageable pageable);

    default List<SoftwareUploadSession> findExpired(Instant now, int limit) {
        return findExpired(now, Pageable.ofSize(Math.max(1, limit)));
    }

    @Query("SELECT s FROM SoftwareUploadSession s WHERE s.status = 'COMPLETING' "
            + "AND s.lastActivityAt < :activityBefore ORDER BY s.lastActivityAt ASC")
    List<SoftwareUploadSession> findStaleCompleting(@Param("activityBefore") Instant activityBefore,
                                                    Pageable pageable);

    default List<SoftwareUploadSession> findStaleCompleting(Instant activityBefore, int limit) {
        return findStaleCompleting(activityBefore, Pageable.ofSize(Math.max(1, limit)));
    }
}
