package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.domain.software.SoftwarePackageService;
import com.iflytek.skillhub.domain.software.SoftwareQuotaService;
import com.iflytek.skillhub.domain.software.SoftwareUploadSession;
import com.iflytek.skillhub.domain.software.SoftwareUploadSessionService;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits an upload completion atomically: package revision, quota
 * confirmation and session completion either all land or none do. Idempotent:
 * a retry after a crash finds the revision created by the previous attempt
 * (same object key) and finishes the remaining steps without double-charging
 * the quota.
 */
@Service
public class SoftwareUploadCompletionService {

    private final SoftwarePackageService packageService;
    private final SoftwareQuotaService quotaService;
    private final SoftwareUploadSessionService sessionService;

    public SoftwareUploadCompletionService(SoftwarePackageService packageService,
                                           SoftwareQuotaService quotaService,
                                           SoftwareUploadSessionService sessionService) {
        this.packageService = packageService;
        this.quotaService = quotaService;
        this.sessionService = sessionService;
    }

    /**
     * Runs inside one transaction. A conflicting SHA-256 belonging to a
     * different object key is a genuine duplicate and aborts the whole
     * completion; the caller then cleans up the merged bytes.
     */
    @Transactional
    public Long finalizeCompletion(Long sessionId, String sha256Hex, long actualByteSize) {
        SoftwareUploadSession session = sessionService.requireSession(sessionId);
        var existing = packageService.findByApplicationIdAndSha256(
                session.getApplicationId(), sha256Hex);
        SoftwarePackageRevision revision;
        if (existing.isPresent()) {
            SoftwarePackageRevision found = existing.get();
            if (!found.getObjectKey().equals(session.getObjectKey())) {
                throw new DomainConflictException("error.software.package.duplicateSha256", sha256Hex);
            }
            // Crash recovery: the previous attempt created the revision but
            // never committed the quota move or the session state.
            revision = found;
        } else {
            revision = packageService.register(session.getReleaseId(), session.getApplicationId(),
                    session.getTargetOs(), session.getTargetArch(), session.getTargetDistro(),
                    session.getApplicabilityNote(), session.getFormat(),
                    session.getOriginalFullVersion(), null, session.getFileName(), sha256Hex,
                    actualByteSize, session.getObjectKey(), session.getOwnerUserId());
        }
        quotaService.confirmReservation(session.getReservedBytes(), actualByteSize);
        sessionService.markCompleted(session.getId(), revision.getId());
        return revision.getId();
    }
}
