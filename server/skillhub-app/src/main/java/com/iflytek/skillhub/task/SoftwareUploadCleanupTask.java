package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.software.SoftwarePolicyService;
import com.iflytek.skillhub.domain.software.SoftwareUploadSession;
import com.iflytek.skillhub.domain.software.SoftwareUploadSessionService;
import com.iflytek.skillhub.domain.software.SoftwareQuotaService;
import com.iflytek.skillhub.service.SoftwareUploadAppService;
import com.iflytek.skillhub.storage.software.SoftwareTransferStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reclaims upload sessions per their own policy snapshots: idle ACTIVE
 * sessions past their expiry and crashed COMPLETING sessions past the failed
 * temp retention window. Each session is locked individually, so a session
 * being completed can never be reclaimed by this task, and a session that
 * races the cleanup is skipped.
 */
@Component
public class SoftwareUploadCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(SoftwareUploadCleanupTask.class);
    private static final int BATCH_SIZE = 20;

    private final SoftwareUploadSessionService sessionService;
    private final SoftwareQuotaService quotaService;
    private final SoftwarePolicyService policyService;
    private final SoftwareTransferStorage transferStorage;

    public SoftwareUploadCleanupTask(SoftwareUploadSessionService sessionService,
                                     SoftwareQuotaService quotaService,
                                     SoftwarePolicyService policyService,
                                     SoftwareTransferStorage transferStorage) {
        this.sessionService = sessionService;
        this.quotaService = quotaService;
        this.policyService = policyService;
        this.transferStorage = transferStorage;
    }

    @Scheduled(fixedDelay = 300000)
    public void reclaimExpiredSessions() {
        Instant now = Instant.now();
        List<SoftwareUploadSession> expired = sessionService.findExpired(now, BATCH_SIZE);
        for (SoftwareUploadSession session : expired) {
            reclaimIdleSession(session.getId(), now);
        }
        long retentionHours = policyService.findActive().config().failedTempRetentionHours();
        Instant completingCutoff = now.minus(Duration.ofHours(retentionHours));
        List<SoftwareUploadSession> staleCompleting =
                sessionService.findStaleCompleting(completingCutoff, BATCH_SIZE);
        for (SoftwareUploadSession session : staleCompleting) {
            reclaimStaleCompleting(session.getId());
        }
    }

    @Transactional
    public void reclaimIdleSession(Long sessionId, Instant now) {
        SoftwareUploadSession session = sessionService.expireIfIdle(sessionId, now);
        if (session == null) {
            return;
        }
        transferStorage.abortUpload(session.getStorageSessionRef(), session.getObjectKey());
        quotaService.releaseReservation(session.getReservedBytes());
        logger.info("Reclaimed idle upload session {} (owner {}, {} reserved bytes released)",
                session.getId(), session.getOwnerUserId(), session.getReservedBytes());
    }

    @Transactional
    public void reclaimStaleCompleting(Long sessionId) {
        SoftwareUploadSession session = sessionService.cancelIfStaleCompleting(sessionId);
        if (session == null) {
            return;
        }
        transferStorage.abortUpload(session.getStorageSessionRef(), session.getObjectKey());
        quotaService.releaseReservation(session.getReservedBytes());
        logger.warn("Reclaimed crashed COMPLETING upload session {} (owner {}, {} reserved bytes released)",
                session.getId(), session.getOwnerUserId(), session.getReservedBytes());
    }
}
