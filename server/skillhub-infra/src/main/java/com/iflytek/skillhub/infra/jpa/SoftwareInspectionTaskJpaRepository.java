package com.iflytek.skillhub.infra.jpa;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA implementation of the inspection task port. The claim query locks
 * candidate rows with FOR UPDATE SKIP LOCKED so concurrent workers inside
 * short claim transactions never receive the same task, mirroring the
 * scan-task outbox dispatch pattern.
 */
public interface SoftwareInspectionTaskJpaRepository
        extends JpaRepository<com.iflytek.skillhub.domain.software.SoftwareInspectionTask, Long>,
        com.iflytek.skillhub.domain.software.SoftwareInspectionTaskRepository {

    @Override
    @Query(value = """
            SELECT * FROM software_inspection_task
            WHERE (status = 'PENDING'
                   AND (next_attempt_not_before IS NULL
                        OR next_attempt_not_before <= CURRENT_TIMESTAMP)
                   AND attempt_count < max_attempts)
               OR (status = 'LEASED' AND lease_expires_at < CURRENT_TIMESTAMP)
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<com.iflytek.skillhub.domain.software.SoftwareInspectionTask> findClaimable(
            @Param("limit") int limit);

    @Override
    @Query(value = """
            SELECT * FROM software_inspection_task
            WHERE status = 'LEASED' AND lease_expires_at < CURRENT_TIMESTAMP
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<com.iflytek.skillhub.domain.software.SoftwareInspectionTask> findExpiredLeases(
            @Param("limit") int limit);

    Page<com.iflytek.skillhub.domain.software.SoftwareInspectionTask> findByStatus(
            com.iflytek.skillhub.domain.software.SoftwareInspectionTaskStatus status, Pageable pageable);

    @Override
    default Page<com.iflytek.skillhub.domain.software.SoftwareInspectionTask> search(
            com.iflytek.skillhub.domain.software.SoftwareInspectionTaskStatus status, Pageable pageable) {
        return status == null ? findAll(pageable) : findByStatus(status, pageable);
    }
}
