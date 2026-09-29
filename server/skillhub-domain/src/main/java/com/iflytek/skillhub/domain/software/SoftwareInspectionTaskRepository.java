package com.iflytek.skillhub.domain.software;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Persistence port for persistent static-inspection tasks. Claiming uses
 * SELECT ... FOR UPDATE SKIP LOCKED so concurrent workers never grab the same
 * task; the claim transaction stays short.
 */
public interface SoftwareInspectionTaskRepository {

    SoftwareInspectionTask save(SoftwareInspectionTask task);

    Optional<SoftwareInspectionTask> findById(Long id);

    Optional<SoftwareInspectionTask> findByPackageRevisionId(Long packageRevisionId);

    /**
     * Tasks due for claiming: pending attempts whose backoff elapsed, plus
     * leases that expired without a result. Skips rows locked by other
     * workers' claim transactions. Due-ness is judged against the database
     * clock so the comparison is exact for timezone-aware columns.
     */
    List<SoftwareInspectionTask> findClaimable(int limit);

    /** Leases that expired without a result, oldest first. */
    List<SoftwareInspectionTask> findExpiredLeases(int limit);

    Page<SoftwareInspectionTask> search(SoftwareInspectionTaskStatus status, Pageable pageable);
}
