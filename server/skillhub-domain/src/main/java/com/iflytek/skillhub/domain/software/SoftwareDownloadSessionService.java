package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and revalidates logical download sessions. Publication and
 * permission checks run again on every range request; the session only avoids
 * double-counting resumed transfers of the same logical download.
 */
@Service
public class SoftwareDownloadSessionService {

    private final SoftwareDownloadSessionRepository sessionRepository;
    private final SoftwarePackageRevisionRepository packageRepository;
    private final SoftwareApplicationRepository applicationRepository;
    private final SoftwarePolicyService policyService;

    public SoftwareDownloadSessionService(SoftwareDownloadSessionRepository sessionRepository,
                                          SoftwarePackageRevisionRepository packageRepository,
                                          SoftwareApplicationRepository applicationRepository,
                                          SoftwarePolicyService policyService) {
        this.sessionRepository = sessionRepository;
        this.packageRepository = packageRepository;
        this.applicationRepository = applicationRepository;
        this.policyService = policyService;
    }

    public record IssuedDownloadSession(SoftwareDownloadSession session, SoftwarePackageRevision revision) {
    }

    /** Issues a session for a downloadable (published app + published package) revision. */
    @Transactional
    public IssuedDownloadSession createSession(Long packageRevisionId, String userId, Instant now) {
        SoftwarePackageRevision revision = packageRepository.findById(packageRevisionId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.software.package.notFound", packageRevisionId));
        if (revision.getStatus() != SoftwarePackageStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.software.download.notPublished",
                    revision.getStatus());
        }
        SoftwareApplication application = applicationRepository.findById(revision.getApplicationId())
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.software.app.notFound", revision.getApplicationId()));
        if (application.getStatus() != SoftwareApplicationStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.software.download.notPublished",
                    application.getStatus());
        }
        int idleMinutes = (int) policyService.requireActive()
                .config().downloadSessionIdleTimeoutMinutes();
        SoftwareDownloadSession session = new SoftwareDownloadSession(
                UUID.randomUUID().toString(), userId, packageRevisionId, true, idleMinutes, now);
        return new IssuedDownloadSession(sessionRepository.save(session), revision);
    }

    /**
     * Loads a live session for streaming. Ownership, expiry and — via the
     * returned revision — the current governance state are all rechecked here;
     * the caller still streams through the storage layer.
     */
    @Transactional
    public ActiveDownload openForDownload(String sessionId, String userId, Instant now) {
        SoftwareDownloadSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.software.download.sessionNotFound", sessionId));
        session.requireOwner(userId);
        if (session.isExpired(now)) {
            throw new DomainNotFoundException("error.software.download.sessionExpired", sessionId);
        }
        SoftwarePackageRevision revision = packageRepository.findById(session.getPackageRevisionId())
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.software.package.notFound", session.getPackageRevisionId()));
        if (revision.getStatus() != SoftwarePackageStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.software.download.notPublished",
                    revision.getStatus());
        }
        SoftwareApplication application = applicationRepository.findById(revision.getApplicationId())
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.software.app.notFound", revision.getApplicationId()));
        if (application.getStatus() != SoftwareApplicationStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.software.download.notPublished",
                    application.getStatus());
        }
        session.touch(now);
        sessionRepository.save(session);
        return new ActiveDownload(session, revision);
    }

    public record ActiveDownload(SoftwareDownloadSession session, SoftwarePackageRevision revision) {
    }
}
