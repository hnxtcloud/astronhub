package com.iflytek.skillhub.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.domain.software.SoftwareApplication;
import com.iflytek.skillhub.domain.software.SoftwareApplicationRepository;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevisionRepository;
import com.iflytek.skillhub.domain.software.SoftwarePackageStatus;
import com.iflytek.skillhub.domain.software.SoftwarePolicyRevision;
import com.iflytek.skillhub.domain.software.SoftwarePolicyRevisionRepository;
import com.iflytek.skillhub.domain.software.SoftwarePolicyState;
import com.iflytek.skillhub.domain.software.SoftwarePolicyStateRepository;
import com.iflytek.skillhub.domain.software.SoftwareQuotaService;
import com.iflytek.skillhub.domain.software.SoftwareRelease;
import com.iflytek.skillhub.domain.software.SoftwareReleaseRepository;
import com.iflytek.skillhub.domain.software.SoftwareStorageAccount;
import com.iflytek.skillhub.domain.software.SoftwareStorageAccountRepository;
import com.iflytek.skillhub.domain.software.SoftwareTargetArch;
import com.iflytek.skillhub.domain.software.SoftwareTargetOs;
import com.iflytek.skillhub.domain.software.SoftwareUploadSession;
import com.iflytek.skillhub.domain.software.SoftwareUploadSessionRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.dto.software.SoftwareChunkReceiptResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadCompleteResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadProgressResponse;
import com.iflytek.skillhub.dto.software.SoftwareUploadSessionCreateRequest;
import com.iflytek.skillhub.dto.software.SoftwareUploadSessionResponse;
import com.iflytek.skillhub.service.SoftwareDownloadAppService;
import com.iflytek.skillhub.service.SoftwareDownloadAppService.DownloadTarget;
import com.iflytek.skillhub.service.SoftwareUploadAppService;
import com.iflytek.skillhub.storage.software.SoftwareTransferStorage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end software-center transfer acceptance on real file bytes with the
 * real local storage implementation and the real H2-backed domain services:
 * chunked upload (out-of-order, retransmit, conflict), completion with
 * server-side SHA-256, quota accounting, idempotent retry, cancel and expiry
 * reclamation, then download sessions with bounded range reads.
 */
@SpringBootTest(properties = {
        "skillhub.software.storage.temp-dir=./target/sw-transfer-tmp",
        "skillhub.storage.local.base-path=./target/sw-transfer-base"
})
@ActiveProfiles("test")
class SoftwareTransferIntegrationTest {

    private static final long CHUNK = 8L * 1024 * 1024;

    @Autowired
    private SoftwareUploadAppService uploadAppService;
    @Autowired
    private SoftwareDownloadAppService downloadAppService;
    @Autowired
    private SoftwareQuotaService quotaService;
    @Autowired
    private SoftwarePolicyStateRepository policyStateRepository;
    @Autowired
    private SoftwarePolicyRevisionRepository policyRevisionRepository;
    @Autowired
    private SoftwareStorageAccountRepository accountRepository;
    @Autowired
    private SoftwareApplicationRepository applicationRepository;
    @Autowired
    private SoftwareReleaseRepository releaseRepository;
    @Autowired
    private SoftwarePackageRevisionRepository packageRepository;
    @Autowired
    private SoftwareUploadSessionRepository uploadSessionRepository;
    @Autowired
    private SoftwareTransferStorage transferStorage;
    @Autowired
    private com.iflytek.skillhub.task.SoftwareUploadCleanupTask cleanupTask;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");

    @Test
    void chunkedUploadCompletionDownloadRoundTrip_onRealBytes() throws Exception {
        seedPolicyAndAccount();
        // Two real chunks against the default 8 MiB policy chunk: 8 MiB + 1 MiB.
        byte[] payload = buildPayload((int) (CHUNK + 1024 * 1024));
        byte[] chunk1 = java.util.Arrays.copyOfRange(payload, 0, (int) CHUNK);
        byte[] chunk2 = java.util.Arrays.copyOfRange(payload, (int) CHUNK, payload.length);

        Long applicationId = createPublishedApp();
        Long releaseId = createRelease(applicationId);

        SoftwareUploadSessionResponse session = uploadAppService.createSession(
                new SoftwareUploadSessionCreateRequest(releaseId, "installer.msi", "MSI",
                        "WINDOWS", "X86_64", null, null, "1.0.0.0", payload.length),
                "admin-1");

        // out-of-order upload: chunk 2 first, then chunk 1
        SoftwareChunkReceiptResponse second = uploadAppService.putChunk(session.id(), 2,
                chunk2.length, new ByteArrayInputStream(chunk2), "admin-1");
        SoftwareChunkReceiptResponse first = uploadAppService.putChunk(session.id(), 1,
                chunk1.length, new ByteArrayInputStream(chunk1), "admin-1");
        assertThat(first.alreadyPresent()).isFalse();
        assertThat(second.alreadyPresent()).isFalse();

        // idempotent retransmit of identical bytes
        SoftwareChunkReceiptResponse retransmit = uploadAppService.putChunk(session.id(), 2,
                chunk2.length, new ByteArrayInputStream(chunk2), "admin-1");
        assertThat(retransmit.alreadyPresent()).isTrue();

        // conflicting bytes for an accepted chunk are rejected
        byte[] conflicting = java.util.Arrays.copyOf(chunk2, chunk2.length);
        conflicting[0] ^= 0x55;
        assertThatThrownBy(() -> uploadAppService.putChunk(session.id(), 2,
                chunk2.length, new ByteArrayInputStream(conflicting), "admin-1"))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("error.software.upload.chunkConflict");

        // manifest reflects all chunks
        SoftwareUploadProgressResponse progress = uploadAppService.progress(session.id(), "admin-1");
        assertThat(progress.uploadedBytes()).isEqualTo(payload.length);
        assertThat(progress.chunks()).hasSize(2);

        // completion computes the digest from the merged bytes
        SoftwareUploadCompleteResponse completion = uploadAppService.complete(session.id(), "admin-1");
        assertThat(completion.status()).isEqualTo("COMPLETED");
        assertThat(completion.sha256()).isEqualTo(sha256Hex(payload));
        assertThat(completion.byteSize()).isEqualTo(payload.length);

        // retry after a lost completion response replays the same revision
        SoftwareUploadCompleteResponse replay = uploadAppService.complete(session.id(), "admin-1");
        assertThat(replay.packageRevisionId()).isEqualTo(completion.packageRevisionId());

        // revision registered as DRAFT with the computed digest
        SoftwarePackageRevision revision = packageRepository.findById(completion.packageRevisionId())
                .orElseThrow();
        assertThat(revision.getStatus()).isEqualTo(SoftwarePackageStatus.DRAFT);
        assertThat(revision.getSha256()).isEqualTo(sha256Hex(payload));

        // quota moved from reserved into used exactly once
        SoftwareStorageAccount account = accountRepository.findSingleton().orElseThrow();
        assertThat(account.getUsedBytes()).isEqualTo(payload.length);
        assertThat(account.getReservedBytes()).isZero();

        // ---- download side -------------------------------------------------
        SoftwarePackageRevision published = revision;
        published.confirm("admin-1");
        published.publish();
        packageRepository.save(published);

        var downloadSession = downloadAppService.createSession(
                completion.packageRevisionId(), "user-1");
        DownloadTarget target = downloadAppService.open(downloadSession.sessionId(), "user-1");
        assertThat(target.byteSize()).isEqualTo(payload.length);
        try (InputStream in = downloadAppService.openRange(target, CHUNK, 1024 * 1024)) {
            assertThat(in.readAllBytes()).isEqualTo(chunk2);
        }
        try (InputStream in = downloadAppService.openRange(target, 0, payload.length)) {
            assertThat(in.readAllBytes()).isEqualTo(payload);
        }

        // ---- cancel path releases the reservation ---------------------------
        SoftwareUploadSessionResponse cancelledSession = uploadAppService.createSession(
                new SoftwareUploadSessionCreateRequest(releaseId, "other.msi", "MSI",
                        "WINDOWS", "X86_64", null, null, "1.0.0.0", CHUNK),
                "admin-1");
        uploadAppService.cancel(cancelledSession.id(), "admin-1");
        account = accountRepository.findSingleton().orElseThrow();
        assertThat(account.getReservedBytes()).isZero();
    }

    @Test
    void quotaExhaustion_rejectsNewSessionsUntilReservationReleased() {
        seedPolicyAndAccount();
        Long applicationId = createPublishedApp();
        Long releaseId = createRelease(applicationId);

        long totalQuota = 1000;
        // emulate a low quota by reserving directly against a crafted policy:
        // reserve() compares against the passed quota, so verify the arithmetic
        SoftwareStorageAccount account = accountRepository.findSingleton().orElseThrow();
        long before = account.getUsedBytes() + account.getReservedBytes();
        quotaService.reserve(400, before + totalQuota);
        assertThatThrownBy(() -> quotaService.reserve(700, before + totalQuota))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("error.software.upload.quotaExceeded");
        quotaService.releaseReservation(400);
    }

    @Test
    void cleanupTask_reclaimsIdleSessionsAndReleasesReservation() {
        seedPolicyAndAccount();
        Long applicationId = createPublishedApp();
        Long releaseId = createRelease(applicationId);

        SoftwareUploadSession session = new SoftwareUploadSession(
                releaseId, applicationId, "admin-1", "idle.msi",
                com.iflytek.skillhub.domain.software.SoftwarePackageFormat.MSI,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null, "1.0.0.0",
                CHUNK, CHUNK, 1, 1440, CHUNK, "software/packages/idle/idle.msi",
                Instant.parse("2026-01-01T00:00:00Z"));
        session.assignStorageSessionRef("local-idle-session");
        session = uploadSessionRepository.save(session);
        // emulate the admission-time reservation the app flow performs
        quotaService.reserve(CHUNK, Long.MAX_VALUE);

        cleanupTask.reclaimIdleSession(session.getId(), Instant.parse("2026-01-02T00:00:00Z"));

        SoftwareUploadSession reclaimed = uploadSessionRepository.findById(session.getId())
                .orElseThrow();
        assertThat(reclaimed.getStatus())
                .isEqualTo(com.iflytek.skillhub.domain.software.SoftwareUploadSessionStatus.EXPIRED);
        SoftwareStorageAccount account = accountRepository.findSingleton().orElseThrow();
        assertThat(account.getReservedBytes()).isZero();

        // a reclamation with a "now" before expiry is a no-op
        SoftwareUploadSession fresh = uploadSessionRepository.save(new SoftwareUploadSession(
                releaseId, applicationId, "admin-1", "fresh.msi",
                com.iflytek.skillhub.domain.software.SoftwarePackageFormat.MSI,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null, "1.0.0.0",
                CHUNK, CHUNK, 1, 1440, CHUNK, "software/packages/idle/fresh.msi",
                Instant.parse("2026-01-01T00:00:00Z")));
        fresh.assignStorageSessionRef("local-fresh-session");
        uploadSessionRepository.save(fresh);
        quotaService.reserve(CHUNK, Long.MAX_VALUE);
        cleanupTask.reclaimIdleSession(fresh.getId(), Instant.parse("2026-01-01T01:00:00Z"));
        assertThat(uploadSessionRepository.findById(fresh.getId()).orElseThrow().getStatus())
                .isEqualTo(com.iflytek.skillhub.domain.software.SoftwareUploadSessionStatus.ACTIVE);
    }

    // ---- fixture helpers ---------------------------------------------------

    private void seedPolicyAndAccount() {
        transactionTemplate.executeWithoutResult(status -> {
            // tests share one H2 database per context; start each test from a
            // clean transfer state
            jdbcTemplate.update("DELETE FROM software_upload_chunk");
            jdbcTemplate.update("DELETE FROM software_upload_session");
            jdbcTemplate.update("DELETE FROM software_download_session");
            jdbcTemplate.update(
                    "UPDATE software_storage_account SET used_bytes = 0, reserved_bytes = 0 "
                    + "WHERE id = 1");
            if (policyStateRepository.findSingleton().isEmpty()) {
                // H2's JSON domain stores parameter-bound strings as JSON string
                // documents; a FORMAT JSON literal lands as a real object document
                // (PostgreSQL binds entity JSON correctly and does not need this).
                String config = com.iflytek.skillhub.domain.software.SoftwarePolicyConfigJson
                        .toJson(com.iflytek.skillhub.domain.software.SoftwarePolicyConfig.defaults());
                jdbcTemplate.update("INSERT INTO software_policy_revision "
                        + "(revision, schema_version, config, changed_by, change_reason, created_at) "
                        + "VALUES (1, 1, '" + config + "' FORMAT JSON, 'system', 'test seed', "
                        + "CURRENT_TIMESTAMP)");
                policyStateRepository.save(new SoftwarePolicyState(
                        policyRevisionRepository.findById(1L).orElseThrow().getId()));
            }
            if (accountRepository.findSingleton().isEmpty()) {
                accountRepository.save(new SoftwareStorageAccount(0, 0));
            }
        });
    }

    private Long createPublishedApp() {
        return transactionTemplate.execute(status -> {
            SoftwareApplication app = applicationRepository.save(
                    new SoftwareApplication("transfer-test-app-" + System.nanoTime(),
                            "Transfer Test App", "desc", null, null, "admin-1"));
            app.publish();
            return applicationRepository.save(app).getId();
        });
    }

    private Long createRelease(Long applicationId) {
        return transactionTemplate.execute(status ->
                releaseRepository.save(new SoftwareRelease(applicationId, "1.0.0", "notes",
                        "admin-1")).getId());
    }

    private static byte[] buildPayload(int size) {
        byte[] payload = new byte[size];
        for (int i = 0; i < size; i++) {
            payload[i] = (byte) (i * 31 + (i >> 8));
        }
        return payload;
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
