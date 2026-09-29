package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SoftwareUploadSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final long CHUNK = 8L * 1024 * 1024;

    private SoftwareUploadSessionRepository sessionRepository;
    private SoftwareUploadChunkRepository chunkRepository;
    private SoftwareReleaseRepository releaseRepository;
    private SoftwarePolicyService policyService;
    private SoftwareQuotaService quotaService;
    private SoftwareUploadSessionService service;
    private SoftwareRelease release;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SoftwareUploadSessionRepository.class);
        chunkRepository = mock(SoftwareUploadChunkRepository.class);
        releaseRepository = mock(SoftwareReleaseRepository.class);
        policyService = mock(SoftwarePolicyService.class);
        quotaService = mock(SoftwareQuotaService.class);
        service = new SoftwareUploadSessionService(sessionRepository, chunkRepository,
                releaseRepository, policyService, quotaService);

        release = new SoftwareRelease(1L, "1.0.0", "notes", "admin-1");
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release));
        when(policyService.requireActive()).thenReturn(new SoftwarePolicySnapshot(
                1, 1, SoftwarePolicyConfig.defaults(), "system", NOW));

        when(sessionRepository.save(any(SoftwareUploadSession.class)))
                .thenAnswer(invocation -> {
                    SoftwareUploadSession session = invocation.getArgument(0);
                    assignId(session, 77L);
                    return session;
                });
        when(chunkRepository.save(any(SoftwareUploadChunk.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createSession_snapshotsChunkSizeTimeoutAndPolicyRevision() {
        SoftwareUploadSessionService.CreatedSession created = service.createSession(
                10L, "admin-1", "app.msi", SoftwarePackageFormat.MSI,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                "1.0.0.0", CHUNK, "software/packages/x/app.msi", NOW);

        SoftwareUploadSession session = created.session();
        assertThat(session.getChunkSizeBytes()).isEqualTo(CHUNK);
        assertThat(session.getPolicyRevision()).isEqualTo(1);
        assertThat(session.getUploadIdleTimeoutMinutes()).isEqualTo(24 * 60);
        assertThat(session.getReservedBytes()).isEqualTo(CHUNK);
        assertThat(session.getStatus()).isEqualTo(SoftwareUploadSessionStatus.ACTIVE);
        assertThat(session.getApplicationId()).isEqualTo(1L);
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plusSeconds(24 * 3600));
    }

    @Test
    void createSession_rejectsWhenSizeExceedsCurrentLimit() {
        assertThatThrownBy(() -> service.createSession(
                10L, "admin-1", "app.msi", SoftwarePackageFormat.MSI,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                "1.0.0.0", 6L * 1024 * 1024 * 1024, "k", NOW))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.software.upload.sizeExceedsLimit");
    }

    @Test
    void createSession_reservesQuotaInAdmissionTransaction() {
        service.createSession(
                10L, "admin-1", "app.msi", SoftwarePackageFormat.MSI,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                "1.0.0.0", CHUNK, "software/packages/x/app.msi", NOW);

        verify(quotaService).reserve(CHUNK, SoftwarePolicyConfig.defaults().totalQuotaBytes());
    }

    @Test
    void createSession_quotaRejectionAbortsAdmission() {
        org.mockito.Mockito.doThrow(new DomainConflictException("error.software.upload.quotaExceeded",
                0, 1)).when(quotaService).reserve(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());

        assertThatThrownBy(() -> service.createSession(
                10L, "admin-1", "app.msi", SoftwarePackageFormat.MSI,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                "1.0.0.0", CHUNK, "software/packages/x/app.msi", NOW))
                .isInstanceOf(DomainConflictException.class);
        verify(sessionRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any(SoftwareUploadSession.class));
    }

    @Test
    void createSession_enforcesParallelUploadLimit() {
        SoftwareUploadSession first = service.createSession(
                10L, "admin-1", "a.msi", SoftwarePackageFormat.MSI,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                "1.0.0.0", CHUNK, "k1", NOW).session();
        assignId(first, 1L);
        when(sessionRepository.countByOwnerUserIdAndStatus(
                "admin-1", SoftwareUploadSessionStatus.ACTIVE)).thenReturn(2L);

        assertThatThrownBy(() -> service.createSession(
                10L, "admin-1", "b.msi", SoftwarePackageFormat.MSI,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                "1.0.0.0", CHUNK, "k2", NOW))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("error.software.upload.parallelLimitExceeded");
    }

    @Test
    void recordChunk_acceptsExactLengthAndRejectsWrongLength() {
        SoftwareUploadSession session = createActiveSession();
        when(chunkRepository.findBySessionIdAndChunkNo(77L, 1)).thenReturn(Optional.empty());

        SoftwareUploadSessionService.ChunkReceipt ok = service.recordChunk(
                77L, "admin-1", 1, 512, "a".repeat(64), NOW);
        assertThat(ok.alreadyPresent()).isFalse();
        assertThat(session.getLastActivityAt()).isEqualTo(NOW);

        assertThatThrownBy(() -> service.recordChunk(77L, "admin-1", 1, 999, "a".repeat(64), NOW))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.software.upload.chunkLengthMismatch");
    }

    @Test
    void recordChunk_retransmitWithSameDigestIsIdempotent() {
        SoftwareUploadSession session = createActiveSession();
        when(chunkRepository.findBySessionIdAndChunkNo(77L, 2))
                .thenReturn(Optional.of(new SoftwareUploadChunk(77L, 2, 512, "abc".repeat(21) + "a")));

        SoftwareUploadSessionService.ChunkReceipt receipt = service.recordChunk(
                77L, "admin-1", 2, 512, "ABC".repeat(21) + "A", NOW);

        assertThat(receipt.alreadyPresent()).isTrue();
        assertThat(session.getLastActivityAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void recordChunk_conflictingDigestNeverReplaces() {
        createActiveSession();
        when(chunkRepository.findBySessionIdAndChunkNo(77L, 2))
                .thenReturn(Optional.of(new SoftwareUploadChunk(77L, 2, 512, "a".repeat(64))));

        assertThatThrownBy(() -> service.recordChunk(
                77L, "admin-1", 2, 512, "b".repeat(64), NOW))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("error.software.upload.chunkConflict");
    }

    @Test
    void recordChunk_rejectsOtherUsersAndInactiveSessions() {
        createActiveSession();

        assertThatThrownBy(() -> service.recordChunk(77L, "other-user", 1, 512, "a".repeat(64), NOW))
                .isInstanceOf(DomainNotFoundException.class);

        SoftwareUploadSession session = new SoftwareUploadSession(
                10L, 1L, "admin-1", "app.msi", SoftwarePackageFormat.MSI,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                "1.0.0.0", 1024, CHUNK, 1, 1440, 1024, "k", NOW);
        assignId(session, 77L);
        session.markCancelled();
        when(sessionRepository.findById(77L)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.recordChunk(77L, "admin-1", 1, 512, "a".repeat(64), NOW))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.software.upload.sessionNotActive");
    }

    @Test
    void beginCompletion_requiresAllChunksAndExactTotal() {
        createActiveSession();
        when(chunkRepository.findBySessionId(77L)).thenReturn(java.util.List.of(
                new SoftwareUploadChunk(77L, 1, 512, "a".repeat(64))));

        assertThatThrownBy(() -> service.beginCompletion(77L, "admin-1"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.software.upload.missingChunks");

        when(chunkRepository.findBySessionId(77L)).thenReturn(java.util.List.of(
                new SoftwareUploadChunk(77L, 1, 640, "a".repeat(64)),
                new SoftwareUploadChunk(77L, 2, 384, "b".repeat(64))));

        assertThatThrownBy(() -> service.beginCompletion(77L, "admin-1"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.software.upload.chunkLengthMismatch");
    }

    @Test
    void beginCompletion_flipsToCompletingAndReturnsOrderedChunks() {
        createActiveSession();
        when(chunkRepository.findBySessionId(77L)).thenReturn(java.util.List.of(
                new SoftwareUploadChunk(77L, 2, 512, "b".repeat(64)),
                new SoftwareUploadChunk(77L, 1, 512, "a".repeat(64))));

        SoftwareUploadSessionService.ManifestCheck check = service.beginCompletion(77L, "admin-1");

        assertThat(check.session().getStatus()).isEqualTo(SoftwareUploadSessionStatus.COMPLETING);
        assertThat(check.orderedChunkNumbers()).containsExactly(1, 2);
    }

    @Test
    void beginCompletion_onCompletedSession_signalsAlreadyCompleted() {
        SoftwareUploadSession session = createActiveSession();
        session.markCompleting();
        session.markCompleted(500L);
        when(sessionRepository.findById(77L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.beginCompletion(77L, "admin-1"))
                .isInstanceOf(SoftwareUploadSessionService.CompletedAlreadyException.class);
    }

    @Test
    void beginCompletion_onCompletingSession_allowsRetry() {
        SoftwareUploadSession session = createActiveSession();
        session.markCompleting();
        when(chunkRepository.findBySessionId(77L)).thenReturn(java.util.List.of(
                new SoftwareUploadChunk(77L, 1, 512, "a".repeat(64)),
                new SoftwareUploadChunk(77L, 2, 512, "b".repeat(64))));

        SoftwareUploadSessionService.ManifestCheck check = service.beginCompletion(77L, "admin-1");

        assertThat(check.session().getStatus()).isEqualTo(SoftwareUploadSessionStatus.COMPLETING);
        assertThat(check.orderedChunkNumbers()).containsExactly(1, 2);
    }

    @Test
    void expireIfIdle_expiresOnlyIdleActiveSessions() {
        SoftwareUploadSession session = createActiveSession();
        when(sessionRepository.findById(77L)).thenReturn(Optional.of(session));

        assertThat(service.expireIfIdle(77L, NOW.plusSeconds(25 * 3600)).getStatus())
                .isEqualTo(SoftwareUploadSessionStatus.EXPIRED);

        SoftwareUploadSession fresh = createActiveSession();
        assignId(fresh, 77L);
        when(sessionRepository.findById(77L)).thenReturn(Optional.of(fresh));
        assertThat(service.expireIfIdle(77L, NOW.plusSeconds(60))).isNull();
        verify(sessionRepository, never()).save(fresh);
    }

    @Test
    void cancel_requiresActiveAndOwner() {
        SoftwareUploadSession session = createActiveSession();
        when(sessionRepository.findById(77L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.cancel(77L, "someone-else"))
                .isInstanceOf(DomainNotFoundException.class);

        SoftwareUploadSession cancelled = service.cancel(77L, "admin-1");
        assertThat(cancelled.getStatus()).isEqualTo(SoftwareUploadSessionStatus.CANCELLED);
    }

    private SoftwareUploadSession createActiveSession() {
        SoftwareUploadSession existing = sessionWithId();
        when(sessionRepository.findById(77L)).thenReturn(Optional.of(existing));
        return existing;
    }

    private SoftwareUploadSession sessionWithId() {
        SoftwareUploadSession session = new SoftwareUploadSession(
                10L, 1L, "admin-1", "app.msi", SoftwarePackageFormat.MSI,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                "1.0.0.0", 1024, 512, 1, 1440, 1024, "software/packages/x/app.msi",
                NOW.minusSeconds(60));
        assignId(session, 77L);
        return session;
    }

    private static void assignId(SoftwareUploadSession session, long id) {
        try {
            var field = SoftwareUploadSession.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(session, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
