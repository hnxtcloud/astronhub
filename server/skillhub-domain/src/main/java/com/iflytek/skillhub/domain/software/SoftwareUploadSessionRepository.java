package com.iflytek.skillhub.domain.software;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SoftwareUploadSessionRepository {

    SoftwareUploadSession save(SoftwareUploadSession session);

    Optional<SoftwareUploadSession> findById(Long id);

    /** Active sessions owned by the user, for the parallel-admission limit. */
    long countByOwnerUserIdAndStatus(String ownerUserId, SoftwareUploadSessionStatus status);

    /** Inactive sessions idled past their snapshot expiry, oldest first. */
    List<SoftwareUploadSession> findExpired(Instant now, int limit);

    /** Crashed COMPLETING sessions with no activity since the cutoff, oldest first. */
    List<SoftwareUploadSession> findStaleCompleting(Instant activityBefore, int limit);
}
