package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chunk manifest and lifecycle rules for upload sessions. Byte transfer is the
 * storage layer's job; this service owns the authoritative DB manifest, the
 * admission limits and the completion preconditions. Policy values snapshot on
 * the session at creation, so retransmits after a config change stay inside
 * the session's original boundaries.
 */
@Service
public class SoftwareUploadSessionService {

    private final SoftwareUploadSessionRepository sessionRepository;
    private final SoftwareUploadChunkRepository chunkRepository;
    private final SoftwareReleaseRepository releaseRepository;
    private final SoftwarePolicyService policyService;
    private final SoftwareQuotaService quotaService;

    public SoftwareUploadSessionService(SoftwareUploadSessionRepository sessionRepository,
                                        SoftwareUploadChunkRepository chunkRepository,
                                        SoftwareReleaseRepository releaseRepository,
                                        SoftwarePolicyService policyService,
                                        SoftwareQuotaService quotaService) {
        this.sessionRepository = sessionRepository;
        this.chunkRepository = chunkRepository;
        this.releaseRepository = releaseRepository;
        this.policyService = policyService;
        this.quotaService = quotaService;
    }

    public record CreatedSession(SoftwareUploadSession session, SoftwarePolicySnapshot policy) {
    }

    public record ChunkReceipt(int chunkNo, long byteSize, String sha256, boolean alreadyPresent) {
    }

    /**
     * Admits a new upload session against the current policy: format, size,
     * parallel-admission limit and quota reservation all happen here.
     */
    @Transactional
    public CreatedSession createSession(Long releaseId,
                                        String ownerUserId,
                                        String fileName,
                                        SoftwarePackageFormat format,
                                        SoftwareTargetOs targetOs,
                                        SoftwareTargetArch targetArch,
                                        String targetDistro,
                                        String applicabilityNote,
                                        String originalFullVersion,
                                        long expectedByteSize,
                                        String objectKey,
                                        Instant now) {
        if (releaseRepository.findById(releaseId).isEmpty()) {
            throw new DomainNotFoundException("error.software.release.notFound", releaseId);
        }
        if (fileName == null || fileName.isBlank() || fileName.length() > 255) {
            throw new DomainBadRequestException("error.software.upload.fileNameInvalid");
        }
        SoftwarePolicySnapshot policy = policyService.requireActive();
        if (expectedByteSize <= 0) {
            throw new DomainBadRequestException("error.software.upload.byteSizeInvalid");
        }
        if (expectedByteSize > policy.config().maxPackageBytes()) {
            throw new DomainBadRequestException("error.software.upload.sizeExceedsLimit",
                    expectedByteSize, policy.config().maxPackageBytes());
        }
        if (policy.config().maxActiveUploadsPerAdmin() > 0) {
            long active = sessionRepository.countByOwnerUserIdAndStatus(
                    ownerUserId, SoftwareUploadSessionStatus.ACTIVE);
            if (active >= policy.config().maxActiveUploadsPerAdmin()) {
                throw new DomainConflictException("error.software.upload.parallelLimitExceeded",
                        active, policy.config().maxActiveUploadsPerAdmin());
            }
        }
        // Capacity is reserved inside the same admission transaction; the
        // pessimistic account lock serializes concurrent admissions.
        quotaService.reserve(expectedByteSize, policy.config().totalQuotaBytes());
        SoftwareUploadSession session = new SoftwareUploadSession(
                releaseId, releaseApplicationId(releaseId), ownerUserId, fileName, format,
                targetOs, targetArch, targetDistro, applicabilityNote, originalFullVersion,
                expectedByteSize, policy.config().chunkSizeBytes(), policy.revision(),
                (int) policy.config().uploadIdleTimeoutMinutes(), expectedByteSize, objectKey, now);
        session = sessionRepository.save(session);
        return new CreatedSession(session, policy);
    }

    /**
     * Records a chunk whose bytes the storage layer already staged. Same digest
     * retransmits are idempotent; a conflicting digest never replaces bytes.
     */
    @Transactional
    public ChunkReceipt recordChunk(Long sessionId,
                                    String ownerUserId,
                                    int chunkNo,
                                    long byteSize,
                                    String sha256Hex,
                                    Instant now) {
        SoftwareUploadSession session = requireSession(sessionId);
        session.requireOwner(ownerUserId);
        session.requireActive();
        if (chunkNo < 1 || chunkNo > session.totalChunks()) {
            throw new DomainBadRequestException("error.software.upload.chunkNotExpected", chunkNo,
                    session.totalChunks());
        }
        long expectedLength = session.expectedChunkLength(chunkNo);
        if (byteSize != expectedLength) {
            throw new DomainBadRequestException("error.software.upload.chunkLengthMismatch",
                    byteSize, expectedLength);
        }
        var existing = chunkRepository.findBySessionIdAndChunkNo(sessionId, chunkNo);
        if (existing.isPresent()) {
            SoftwareUploadChunk chunk = existing.get();
            if (!chunk.getSha256().equalsIgnoreCase(sha256Hex) || chunk.getByteSize() != byteSize) {
                throw new DomainConflictException("error.software.upload.chunkConflict", chunkNo);
            }
            return new ChunkReceipt(chunkNo, byteSize, sha256Hex, true);
        }
        chunkRepository.save(new SoftwareUploadChunk(sessionId, chunkNo, byteSize, sha256Hex));
        session.recordChunkActivity(now);
        sessionRepository.save(session);
        return new ChunkReceipt(chunkNo, byteSize, sha256Hex, false);
    }

    /** Manifest view for cross-page resume: the client revalidates against this. */
    public record SessionProgress(SoftwareUploadSession session,
                                  List<ChunkReceipt> uploadedChunks,
                                  long uploadedBytes) {
    }

    @Transactional
    public SessionProgress progress(Long sessionId, String ownerUserId, Instant now) {
        SoftwareUploadSession session = requireSession(sessionId);
        session.requireOwner(ownerUserId);
        List<ChunkReceipt> chunks = chunkRepository.findBySessionId(sessionId).stream()
                .sorted(Comparator.comparingInt(SoftwareUploadChunk::getChunkNo))
                .map(chunk -> new ChunkReceipt(chunk.getChunkNo(), chunk.getByteSize(),
                        chunk.getSha256(), true))
                .toList();
        long uploadedBytes = chunks.stream().mapToLong(ChunkReceipt::byteSize).sum();
        return new SessionProgress(session, chunks, uploadedBytes);
    }

    public record ManifestCheck(SoftwareUploadSession session, List<Integer> orderedChunkNumbers) {
    }

    /**
     * Validates completion preconditions and flips the session to COMPLETING so
     * expiry cleanup cannot reclaim the bytes mid-merge. The caller performs the
     * storage merge outside this transaction and then calls {@link #markCompleted}.
     * A retry on a crashed COMPLETING session re-runs the manifest validation so
     * the merge can be repeated safely.
     */
    @Transactional
    public ManifestCheck beginCompletion(Long sessionId, String ownerUserId) {
        SoftwareUploadSession session = requireSession(sessionId);
        session.requireOwner(ownerUserId);
        if (session.getStatus() == SoftwareUploadSessionStatus.COMPLETED
                && session.getCompletedRevisionId() != null) {
            throw new CompletedAlreadyException(session);
        }
        if (session.getStatus() != SoftwareUploadSessionStatus.ACTIVE
                && session.getStatus() != SoftwareUploadSessionStatus.COMPLETING) {
            throw new DomainBadRequestException("error.software.upload.sessionNotActive",
                    session.getStatus());
        }
        List<SoftwareUploadChunk> chunks = chunkRepository.findBySessionId(sessionId);
        if (chunks.size() != session.totalChunks()) {
            throw new DomainBadRequestException("error.software.upload.missingChunks",
                    chunks.size(), session.totalChunks());
        }
        long total = chunks.stream().mapToLong(SoftwareUploadChunk::getByteSize).sum();
        if (total != session.getExpectedByteSize()) {
            throw new DomainBadRequestException("error.software.upload.totalLengthMismatch",
                    total, session.getExpectedByteSize());
        }
        for (SoftwareUploadChunk chunk : chunks) {
            if (chunk.getByteSize() != session.expectedChunkLength(chunk.getChunkNo())) {
                throw new DomainBadRequestException("error.software.upload.chunkLengthMismatch",
                        chunk.getByteSize(), session.expectedChunkLength(chunk.getChunkNo()));
            }
        }
        session.markCompleting();
        sessionRepository.save(session);
        List<Integer> ordered = chunks.stream()
                .sorted(Comparator.comparingInt(SoftwareUploadChunk::getChunkNo))
                .map(SoftwareUploadChunk::getChunkNo)
                .toList();
        return new ManifestCheck(session, ordered);
    }

    /** Signals that the previous completion attempt already produced a revision. */
    public static class CompletedAlreadyException extends RuntimeException {
        private final SoftwareUploadSession session;

        public CompletedAlreadyException(SoftwareUploadSession session) {
            super("upload session already completed: " + session.getId());
            this.session = session;
        }

        public SoftwareUploadSession getSession() {
            return session;
        }
    }

    /** Final transition after the storage merge and revision creation committed. */
    @Transactional
    public void markCompleted(Long sessionId, Long packageRevisionId) {
        SoftwareUploadSession session = requireSession(sessionId);
        session.markCompleted(packageRevisionId);
        sessionRepository.save(session);
    }

    /** Idempotent lookup used to replay a lost completion response. */
    @Transactional
    public SoftwareUploadSession requireCompletedResult(Long sessionId, String ownerUserId) {
        SoftwareUploadSession session = requireSession(sessionId);
        session.requireOwner(ownerUserId);
        if (session.getStatus() == SoftwareUploadSessionStatus.COMPLETED
                && session.getCompletedRevisionId() != null) {
            return session;
        }
        throw new DomainBadRequestException("error.software.upload.sessionNotCompleted",
                session.getStatus());
    }

    @Transactional
    public SoftwareUploadSession cancel(Long sessionId, String ownerUserId) {
        SoftwareUploadSession session = requireSession(sessionId);
        session.requireOwner(ownerUserId);
        session.requireActive();
        session.markCancelled();
        return sessionRepository.save(session);
    }

    /** Locks and expires one idle session for the cleanup task; returns null if raced. */
    @Transactional
    public SoftwareUploadSession expireIfIdle(Long sessionId, Instant now) {
        SoftwareUploadSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !session.isIdleExpired(now)) {
            return null;
        }
        session.markExpired();
        return sessionRepository.save(session);
    }

    public List<SoftwareUploadSession> findExpired(Instant now, int limit) {
        return sessionRepository.findExpired(now, limit);
    }

    public List<SoftwareUploadSession> findStaleCompleting(Instant activityBefore, int limit) {
        return sessionRepository.findStaleCompleting(activityBefore, limit);
    }

    /** Locks and cancels one stale COMPLETING session; returns null if it raced. */
    @Transactional
    public SoftwareUploadSession cancelIfStaleCompleting(Long sessionId) {
        SoftwareUploadSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != SoftwareUploadSessionStatus.COMPLETING) {
            return null;
        }
        session.markCancelledFromCompleting();
        return sessionRepository.save(session);
    }

    public Set<Integer> chunkNumbers(Long sessionId) {
        return chunkRepository.findBySessionId(sessionId).stream()
                .map(SoftwareUploadChunk::getChunkNo)
                .collect(Collectors.toSet());
    }

    public SoftwareUploadSession requireSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.software.upload.sessionNotFound", sessionId));
    }

    private Long releaseApplicationId(Long releaseId) {
        return releaseRepository.findById(releaseId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.software.release.notFound", releaseId))
                .getApplicationId();
    }
}
