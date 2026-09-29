package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.software.SoftwareDownloadSession;
import com.iflytek.skillhub.domain.software.SoftwareDownloadSessionService;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.dto.software.SoftwareDownloadSessionResponse;
import com.iflytek.skillhub.storage.software.SoftwareTransferStorage;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Issues logical download sessions and exposes storage-backed reads for the
 * streaming endpoint. Publication and permission re-checks live in the domain
 * service; this class only wires storage access and response metadata.
 */
@Service
public class SoftwareDownloadAppService {

    private final SoftwareDownloadSessionService sessionService;
    private final SoftwareTransferStorage transferStorage;
    private final Clock clock;

    public SoftwareDownloadAppService(SoftwareDownloadSessionService sessionService,
                                      SoftwareTransferStorage transferStorage,
                                      Clock clock) {
        this.sessionService = sessionService;
        this.transferStorage = transferStorage;
        this.clock = clock;
    }

    public SoftwareDownloadSessionResponse createSession(Long packageRevisionId, String userId) {
        SoftwareDownloadSessionService.IssuedDownloadSession issued =
                sessionService.createSession(packageRevisionId, userId, Instant.now(clock));
        SoftwareDownloadSession session = issued.session();
        return new SoftwareDownloadSessionResponse(session.getId(),
                "/api/v1/software/downloads/" + session.getId(), session.getExpiresAt());
    }

    /**
     * Revalidates the session and governance state, then resolves the object
     * size so the controller can negotiate Range/206/416 without loading bytes.
     */
    public DownloadTarget open(String sessionId, String userId) {
        SoftwareDownloadSessionService.ActiveDownload active =
                sessionService.openForDownload(sessionId, userId, Instant.now(clock));
        SoftwarePackageRevision revision = active.revision();
        long size = transferStorage.objectSize(revision.getObjectKey());
        return new DownloadTarget(active.session(), revision, size);
    }

    /** Bounded read of {@code length} bytes at {@code offset}; caller closes the stream. */
    public java.io.InputStream openRange(DownloadTarget target, long offset, long length) {
        return transferStorage.readRange(target.revision().getObjectKey(), offset, length);
    }

    public record DownloadTarget(SoftwareDownloadSession session,
                                 SoftwarePackageRevision revision,
                                 long byteSize) {
    }
}
