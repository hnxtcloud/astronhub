package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.software.SoftwarePackageFormat;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.domain.software.SoftwarePackageService;
import com.iflytek.skillhub.domain.software.SoftwareQuotaService;
import com.iflytek.skillhub.domain.software.SoftwareTargetArch;
import com.iflytek.skillhub.domain.software.SoftwareTargetOs;
import com.iflytek.skillhub.domain.software.SoftwareUploadSession;
import com.iflytek.skillhub.domain.software.SoftwareUploadSessionService;
import com.iflytek.skillhub.domain.software.SoftwareUploadSessionStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.dto.software.SoftwareChunkReceiptResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadCompleteResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadProgressResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadSessionCreateRequest;
import com.iflytek.skillhub.dto.software.SoftwareUploadSessionResponse;
import com.iflytek.skillhub.storage.software.SoftwareTransferStorage;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates chunked uploads: domain session state, quota accounting and
 * storage byte transfer. The final SHA-256 always comes from the storage
 * layer's full read of the merged bytes, never from the client.
 */
@Service
public class SoftwareUploadAppService {

    private static final Logger log = LoggerFactory.getLogger(SoftwareUploadAppService.class);

    private final SoftwareUploadSessionService sessionService;
    private final SoftwareQuotaService quotaService;
    private final SoftwarePackageService packageService;
    private final SoftwareUploadCompletionService completionService;
    private final SoftwareTransferStorage transferStorage;
    private final Clock clock;

    public SoftwareUploadAppService(SoftwareUploadSessionService sessionService,
                                    SoftwareQuotaService quotaService,
                                    SoftwarePackageService packageService,
                                    SoftwareUploadCompletionService completionService,
                                    SoftwareTransferStorage transferStorage,
                                    Clock clock) {
        this.sessionService = sessionService;
        this.quotaService = quotaService;
        this.packageService = packageService;
        this.completionService = completionService;
        this.transferStorage = transferStorage;
        this.clock = clock;
    }

    @Transactional
    public SoftwareUploadSessionResponse createSession(SoftwareUploadSessionCreateRequest request,
                                                       String ownerUserId) {
        SoftwarePackageFormat format = parseFormat(request.format());
        SoftwareTargetOs targetOs = parseEnum(SoftwareTargetOs.class, request.targetOs(), "targetOs");
        SoftwareTargetArch targetArch = parseEnum(SoftwareTargetArch.class, request.targetArch(), "targetArch");
        String objectKey = "software/packages/" + UUID.randomUUID() + "/" + sanitizeFileName(request.fileName());
        Instant now = Instant.now(clock);
        SoftwareUploadSessionService.CreatedSession created = sessionService.createSession(
                request.releaseId(), ownerUserId, request.fileName(), format, targetOs, targetArch,
                blankToNull(request.targetDistro()), blankToNull(request.applicabilityNote()),
                request.originalFullVersion(), request.byteSize(), objectKey, now);
        SoftwareUploadSession session = created.session();
        String storageRef = transferStorage.initiateUpload(objectKey);
        session.assignStorageSessionRef(storageRef);
        return toSessionResponse(session);
    }

    /**
     * Stages the chunk bytes, reconciles with the DB manifest, and only then
     * commits storage. Identical retransmits heal missing provider parts; a
     * digest conflict discards the staged bytes untouched and returns 409.
     */
    public SoftwareChunkReceiptResponse putChunk(Long sessionId, int chunkNo, long declaredLength,
                                                 InputStream body, String ownerUserId) {
        SoftwareUploadSession session = sessionService.requireSession(sessionId);
        session.requireOwner(ownerUserId);
        session.requireActive();
        long expectedLength = session.expectedChunkLength(chunkNo);
        if (declaredLength != expectedLength) {
            throw new DomainBadRequestException("error.software.upload.chunkLengthMismatch",
                    declaredLength, expectedLength);
        }
        SoftwareTransferStorage.StagedChunk staged =
                transferStorage.stageChunk(session.getStorageSessionRef(), chunkNo, body, declaredLength);
        try {
            SoftwareUploadSessionService.ChunkReceipt receipt = sessionService.recordChunk(
                    sessionId, ownerUserId, chunkNo, staged.byteSize(), staged.sha256Hex(),
                    Instant.now(clock));
            transferStorage.commitChunk(session.getStorageSessionRef(), session.getObjectKey(), chunkNo);
            return new SoftwareChunkReceiptResponse(
                    receipt.chunkNo(), receipt.byteSize(), receipt.sha256(), receipt.alreadyPresent());
        } catch (RuntimeException e) {
            transferStorage.discardChunk(session.getStorageSessionRef(), chunkNo);
            throw e;
        }
    }

    public SoftwareUploadProgressResponse progress(Long sessionId, String ownerUserId) {
        SoftwareUploadSessionService.SessionProgress progress =
                sessionService.progress(sessionId, ownerUserId, Instant.now(clock));
        List<SoftwareUploadProgressResponse.UploadedChunk> chunks = progress.uploadedChunks().stream()
                .map(chunk -> new SoftwareUploadProgressResponse.UploadedChunk(
                        chunk.chunkNo(), chunk.byteSize(), chunk.sha256()))
                .toList();
        SoftwareUploadSession session = progress.session();
        return new SoftwareUploadProgressResponse(session.getId(), session.getStatus().name(),
                session.totalChunks(), session.getChunkSizeBytes(), progress.uploadedBytes(), chunks,
                session.getExpiresAt());
    }

    /**
     * Completes the upload: merge/verify bytes on the provider (no DB
     * transaction), then commit revision + quota + session state atomically.
     * A lost response replays the same result without re-creating anything.
     * A genuine duplicate SHA-256 cleans up the merged bytes, releases the
     * reservation and surfaces as 409.
     */
    public SoftwareUploadCompleteResponse complete(Long sessionId, String ownerUserId) {
        SoftwareUploadSession session;
        List<Integer> orderedChunkNos;
        try {
            SoftwareUploadSessionService.ManifestCheck check =
                    sessionService.beginCompletion(sessionId, ownerUserId);
            session = check.session();
            orderedChunkNos = check.orderedChunkNumbers();
        } catch (SoftwareUploadSessionService.CompletedAlreadyException already) {
            return toCompleteResponse(already.getSession());
        }

        SoftwareTransferStorage.CompleteResult result = transferStorage.completeUpload(
                session.getStorageSessionRef(), session.getObjectKey(), orderedChunkNos);
        if (result.byteSize() != session.getExpectedByteSize()) {
            // Provider state disagrees with the accepted manifest: keep the
            // reservation and the COMPLETING state for the cleanup task.
            throw new DomainBadRequestException("error.software.upload.completedLengthMismatch",
                    result.byteSize(), session.getExpectedByteSize());
        }
        Long revisionId;
        try {
            revisionId = completionService.finalizeCompletion(sessionId, result.sha256Hex(),
                    result.byteSize());
        } catch (DomainConflictException duplicate) {
            abandonCompletedBytes(session);
            throw duplicate;
        }
        session = sessionService.requireSession(sessionId);
        return toCompleteResponse(session, revisionId);
    }

    @Transactional
    public SoftwareUploadSessionResponse cancel(Long sessionId, String ownerUserId) {
        SoftwareUploadSession session = sessionService.cancel(sessionId, ownerUserId);
        transferStorage.abortUpload(session.getStorageSessionRef(), session.getObjectKey());
        quotaService.releaseReservation(session.getReservedBytes());
        return toSessionResponse(session);
    }

    private void abandonCompletedBytes(SoftwareUploadSession session) {
        try {
            transferStorage.deleteObject(session.getObjectKey());
        } catch (RuntimeException cleanupFailure) {
            log.warn("Failed to delete merged object {} after duplicate-SHA conflict; "
                    + "cleanup task will retry", session.getObjectKey(), cleanupFailure);
        }
        try {
            SoftwareUploadSession current = sessionService.requireSession(session.getId());
            if (current.getStatus() == SoftwareUploadSessionStatus.COMPLETING) {
                sessionService.cancelIfStaleCompleting(session.getId());
            }
        } catch (RuntimeException cleanupFailure) {
            log.warn("Failed to cancel session {} after duplicate-SHA conflict; "
                    + "cleanup task will retry", session.getId(), cleanupFailure);
        }
        quotaService.releaseReservation(session.getReservedBytes());
    }

    private SoftwareUploadCompleteResponse toCompleteResponse(SoftwareUploadSession session,
                                                               Long revisionId) {
        SoftwarePackageRevision revision = packageService.requirePackage(revisionId);
        return new SoftwareUploadCompleteResponse(session.getId(), session.getStatus().name(),
                revision.getId(), revision.getRevisionNo(), revision.getFileName(),
                revision.getSha256(), revision.getByteSize());
    }

    private SoftwareUploadCompleteResponse toCompleteResponse(SoftwareUploadSession session) {
        if (session.getCompletedRevisionId() == null) {
            throw new DomainBadRequestException("error.software.upload.sessionNotCompleted",
                    session.getStatus());
        }
        return toCompleteResponse(session, session.getCompletedRevisionId());
    }

    private SoftwareUploadSessionResponse toSessionResponse(SoftwareUploadSession session) {
        return new SoftwareUploadSessionResponse(session.getId(), session.getStatus().name(),
                session.getFileName(), session.getFormat().name(), session.getTargetOs().name(),
                session.getTargetArch().name(), session.getTargetDistro(), session.getExpectedByteSize(),
                session.getChunkSizeBytes(), session.totalChunks(), 0, session.getPolicyRevision(),
                session.getLastActivityAt(), session.getExpiresAt());
    }

    private SoftwarePackageFormat parseFormat(String value) {
        if (value == null) {
            throw new DomainBadRequestException("error.software.upload.formatRequired");
        }
        try {
            // formats are canonical upper case; clients may send "zip", "Zip", "ZIP"
            return SoftwarePackageFormat.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.software.upload.formatNotAllowed", value);
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null) {
            throw new DomainBadRequestException("error.software.upload.fieldRequired", field);
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("error.software.upload.fieldRequired", field);
        }
    }

    private String sanitizeFileName(String fileName) {
        String cleaned = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "package.bin" : cleaned;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
