package com.iflytek.skillhub.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.software.SoftwareApplication;
import com.iflytek.skillhub.domain.software.SoftwareApplicationRepository;
import com.iflytek.skillhub.domain.software.SoftwareInspectionTaskRepository;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevisionRepository;
import com.iflytek.skillhub.domain.software.SoftwarePolicyConfig;
import com.iflytek.skillhub.domain.software.SoftwarePolicyConfigJson;
import com.iflytek.skillhub.domain.software.SoftwarePolicyRevisionRepository;
import com.iflytek.skillhub.domain.software.SoftwarePolicyState;
import com.iflytek.skillhub.domain.software.SoftwarePolicyStateRepository;
import com.iflytek.skillhub.domain.software.SoftwareRelease;
import com.iflytek.skillhub.domain.software.SoftwareReleaseRepository;
import com.iflytek.skillhub.domain.software.SoftwareStorageAccount;
import com.iflytek.skillhub.domain.software.SoftwareStorageAccountRepository;
import com.iflytek.skillhub.dto.software.SoftwareUploadSessionCreateRequest;
import com.iflytek.skillhub.dto.software.SoftwareUploadSessionResponse;
import com.iflytek.skillhub.service.SoftwareUploadAppService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end static-inspection flow against real H2 + local transfer storage:
 * upload completion creates persistent tasks for auto-inspected formats only,
 * the internal worker API claims/streams/reports under the shared token,
 * stale attempts are rejected, basic failures block confirmation, metadata
 * failures accept manual fill-in, and retry budgets behave.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "skillhub.software.inspector.token=test-inspector-token",
        "skillhub.software.storage.temp-dir=./target/sw-inspection-tmp",
        "skillhub.storage.local.base-path=./target/sw-inspection-base"
})
class SoftwareInspectionIntegrationTest {

    private static final String INSPECTOR_TOKEN = "test-inspector-token";
    private static final byte[] PAYLOAD = "MSI-PAYLOAD-FOR-INSPECTION-TEST".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SoftwareUploadAppService uploadAppService;
    @Autowired
    private SoftwareInspectionTaskRepository inspectionTaskRepository;
    @Autowired
    private SoftwarePackageRevisionRepository packageRepository;
    @Autowired
    private SoftwareApplicationRepository applicationRepository;
    @Autowired
    private SoftwareReleaseRepository releaseRepository;
    @Autowired
    private SoftwarePolicyStateRepository policyStateRepository;
    @Autowired
    private SoftwarePolicyRevisionRepository policyRevisionRepository;
    @Autowired
    private SoftwareStorageAccountRepository accountRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private com.iflytek.skillhub.task.SoftwareInspectionReclaimTask reclaimTask;
    @Autowired
    private com.iflytek.skillhub.domain.software.SoftwareInspectionTaskService inspectionTaskService;

    // ---- internal inspector API -------------------------------------------

    @Test
    void internalApi_rejectsMissingOrWrongToken() throws Exception {
        seedPolicyAndAccount();
        mockMvc.perform(post("/api/v1/internal/software/inspector/tasks/claim")
                        .content("{\"workerId\":\"w1\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/internal/software/inspector/tasks/claim")
                        .header("X-Inspector-Token", "wrong")
                        .content("{\"workerId\":\"w1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalApi_claimWithNoWorkReturnsUnclaimed() throws Exception {
        seedPolicyAndAccount();
        mockMvc.perform(post("/api/v1/internal/software/inspector/tasks/claim")
                        .header("X-Inspector-Token", INSPECTOR_TOKEN)
                        .contentType("application/json")
                        .content("{\"workerId\":\"w1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(false));
    }

    @Test
    void uploadCompletion_createsTaskOnlyForAutoInspectedFormats() throws Exception {
        seedPolicyAndAccount();
        Long releaseId = createRelease(createPublishedApp());

        Long msiRevision = completeUpload(releaseId, "installer.msi", "MSI");
        var msiTask = inspectionTaskRepository.findByPackageRevisionId(msiRevision).orElseThrow();
        assertThat(msiTask.getStatus().name()).isEqualTo("PENDING");
        assertThat(msiTask.getMaxAttempts()).isEqualTo(3);
        assertThat(msiTask.getObjectKey()).isEqualTo(
                packageRepository.findById(msiRevision).orElseThrow().getObjectKey());

        byte[] zipPayload = "ZIP-PAYLOAD-DIFFERENT-CONTENT".getBytes(StandardCharsets.UTF_8);
        Long zipRevision = completeUpload(releaseId, "bundle.zip", "ZIP", zipPayload);
        assertThat(inspectionTaskRepository.findByPackageRevisionId(zipRevision)).isEmpty();
    }

    @Test
    void workerClaimStreamResult_roundTripUpdatesRevisionAndAttempts() throws Exception {
        seedPolicyAndAccount();
        Long releaseId = createRelease(createPublishedApp());
        Long revisionId = completeUpload(releaseId, "installer.msi", "MSI");

        String claim = mockMvc.perform(post("/api/v1/internal/software/inspector/tasks/claim")
                        .header("X-Inspector-Token", INSPECTOR_TOKEN)
                        .contentType("application/json")
                        .content("{\"workerId\":\"worker-a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(true))
                .andExpect(jsonPath("$.format").value("MSI"))
                .andExpect(jsonPath("$.attemptNo").value(1))
                .andReturn().getResponse().getContentAsString();
        long taskId = Long.parseLong(claim.replaceAll(".*\"taskId\":(\\d+).*", "$1"));
        String token = extractJsonString(claim, "attemptToken");

        // worker streams the immutable bytes by task id
        byte[] served = mockMvc.perform(get("/api/v1/internal/software/inspector/tasks/" + taskId + "/content")
                        .header("X-Inspector-Token", INSPECTOR_TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(PAYLOAD);
        assertThat(sha256Hex(served)).isEqualTo(
                packageRepository.findById(revisionId).orElseThrow().getSha256());

        // heartbeat with the attempt token extends the lease
        mockMvc.perform(post("/api/v1/internal/software/inspector/tasks/" + taskId + "/heartbeat")
                        .header("X-Inspector-Token", INSPECTOR_TOKEN)
                        .contentType("application/json")
                        .content("{\"workerId\":\"worker-a\",\"attemptToken\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        // a stale token from a foreign attempt is rejected
        mockMvc.perform(post("/api/v1/internal/software/inspector/tasks/" + taskId + "/result")
                        .header("X-Inspector-Token", INSPECTOR_TOKEN)
                        .contentType("application/json")
                        .content(resultBody("worker-a", "forged", "SUCCEEDED", null, "{\"v\":\"1\"}")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/internal/software/inspector/tasks/" + taskId + "/result")
                        .header("X-Inspector-Token", INSPECTOR_TOKEN)
                        .contentType("application/json")
                        .content(resultBody("worker-a", token, "SUCCEEDED", null,
                                "{\"ProductName\":\"App\",\"ProductVersion\":\"1.2.3\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        SoftwarePackageRevision revision = packageRepository.findById(revisionId).orElseThrow();
        assertThat(revision.getRawVersionFields()).contains("ProductVersion");

        // admin console shows the task and the recorded attempt
        mockMvc.perform(get("/api/v1/admin/software/inspections")
                        .with(authentication(softwareAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].taskId").value(taskId));
        mockMvc.perform(get("/api/v1/admin/software/inspections/" + taskId)
                        .with(authentication(softwareAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.attempts[0].outcome").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.attempts[0].parserName").value("msiinfo"));
    }

    @Test
    void metadataFailed_acceptsManualFillInAndConfirm() throws Exception {
        seedPolicyAndAccount();
        Long releaseId = createRelease(createPublishedApp());
        Long revisionId = completeUpload(releaseId, "installer.msi", "MSI");
        long taskId = claimAndReport(revisionId, "METADATA_FAILED", "parser.msiinfoMissing", null);

        // plain confirmation must not silently waive the metadata failure
        mockMvc.perform(post("/api/v1/admin/software/packages/" + revisionId + "/confirm")
                        .with(authentication(softwareAdmin()))
                        .with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/admin/software/inspections/" + taskId + "/confirm-metadata")
                        .with(authentication(softwareAdmin()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"rawVersionFields\":{\"Version\":\"9.9.9\"}}"))
                .andExpect(status().isOk());

        SoftwarePackageRevision revision = packageRepository.findById(revisionId).orElseThrow();
        assertThat(revision.getRawVersionFields()).contains("9.9.9");
        assertThat(revision.isConfirmed()).isTrue();
    }

    @Test
    void pendingInspection_blocksConfirmationAndManualFillIn() throws Exception {
        seedPolicyAndAccount();
        Long releaseId = createRelease(createPublishedApp());
        Long revisionId = completeUpload(releaseId, "installer.msi", "MSI");

        // the task exists and no worker has reported: required validation is incomplete
        mockMvc.perform(post("/api/v1/admin/software/packages/" + revisionId + "/confirm")
                        .with(authentication(softwareAdmin()))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"rawVersionFields\":{\"Version\":\"1.0.0\"}}"))
                .andExpect(status().isConflict());

        SoftwarePackageRevision revision = packageRepository.findById(revisionId).orElseThrow();
        assertThat(revision.isConfirmed()).isFalse();
    }

    @Test
    void basicFailed_blocksPackageConfirmAndManualRetryRequeues() throws Exception {
        seedPolicyAndAccount();
        Long releaseId = createRelease(createPublishedApp());
        Long revisionId = completeUpload(releaseId, "installer.msi", "MSI");
        long taskId = claimAndReport(revisionId, "BASIC_FAILED", "validation.pathTraversal", null);

        mockMvc.perform(post("/api/v1/admin/software/packages/" + revisionId + "/confirm")
                        .with(authentication(softwareAdmin()))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.containsString("basic static inspection"),
                        org.hamcrest.Matchers.containsString("inspectionFailed"))));

        // controlled manual retry requeues the task with a fresh budget
        mockMvc.perform(post("/api/v1/admin/software/inspections/" + taskId + "/retry")
                        .with(authentication(softwareAdmin()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.manualRetryCount").value(1));
    }

    @Test
    void infraFailure_exhaustsBudgetAndManualRetryResets() throws Exception {
        seedPolicyAndAccount();
        Long releaseId = createRelease(createPublishedApp());
        Long revisionId = completeUpload(releaseId, "installer.msi", "MSI");
        long taskId = inspectionTaskRepository.findByPackageRevisionId(revisionId).orElseThrow().getId();

        // three auto attempts, all failing with recoverable infrastructure errors
        for (int attempt = 1; attempt <= 3; attempt++) {
            // the backoff from the previous failure would delay the claim; run
            // the claim through the service with a shifted clock instead of HTTP
            var claimed = claimViaServiceShifted(taskId, attempt);
            mockMvc.perform(post("/api/v1/internal/software/inspector/tasks/" + taskId + "/result")
                            .header("X-Inspector-Token", INSPECTOR_TOKEN)
                            .contentType("application/json")
                            .content(resultBody("worker-a", claimed,
                                    "INFRA_FAILED", "worker.lost", null)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/admin/software/inspections/" + taskId)
                        .with(authentication(softwareAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("EXHAUSTED"))
                .andExpect(jsonPath("$.data.attempts.length()").value(3));

        // manual retry grants a fresh auto budget: claimable again immediately.
        // Attempt numbering stays monotonic (audit history), so the retried
        // physical attempt is number 4 and the budget horizon extends to 6.
        mockMvc.perform(post("/api/v1/admin/software/inspections/" + taskId + "/retry")
                        .with(authentication(softwareAdmin()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.attemptCount").value(3))
                .andExpect(jsonPath("$.data.maxAttempts").value(6));
        mockMvc.perform(post("/api/v1/internal/software/inspector/tasks/claim")
                        .header("X-Inspector-Token", INSPECTOR_TOKEN)
                        .contentType("application/json")
                        .content("{\"workerId\":\"worker-a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(true))
                .andExpect(jsonPath("$.attemptNo").value(4));
    }

    @Test
    void expiredLeaseWithSpentBudget_isClosedByReclaimPass() throws Exception {
        seedPolicyAndAccount();
        Long releaseId = createRelease(createPublishedApp());
        Long revisionId = completeUpload(releaseId, "installer.msi", "MSI");
        long taskId = inspectionTaskRepository.findByPackageRevisionId(revisionId).orElseThrow().getId();

        claimViaServiceShifted(taskId, 1);
        // force the lease into the past through the domain (Hibernate's own
        // Instant binding) and spend the auto budget with a plain int update;
        // a raw Instant bind would use different JDBC semantics than the
        // timezone-aware comparison on the query side
        var leased = inspectionTaskRepository.findById(taskId).orElseThrow();
        leased.heartbeat("worker-a", leased.getAttemptToken(), Instant.now().minusSeconds(3600));
        inspectionTaskRepository.save(leased);
        jdbcTemplate.update("UPDATE software_inspection_task "
                + "SET attempt_count = max_attempts WHERE id = ?", taskId);

        reclaimTask.reclaimExpiredLeases();

        var task = inspectionTaskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus().name()).isEqualTo("EXHAUSTED");
        mockMvc.perform(get("/api/v1/admin/software/inspections/" + taskId)
                        .with(authentication(softwareAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempts[0].errorCode")
                        .value("error.software.inspection.leaseExpired"));
    }

    @Test
    void adminConsole_requiresSoftwareAdminRole() throws Exception {
        seedPolicyAndAccount();
        mockMvc.perform(get("/api/v1/admin/software/inspections")
                        .with(authentication(regularUser())))
                .andExpect(status().isForbidden());
    }

    // ---- fixture helpers ---------------------------------------------------

    /** Claims through HTTP and reports the given outcome; returns the attempt token. */
    private long claimAndReport(Long revisionId, String outcome, String errorCode,
                                String rawFields) throws Exception {
        long taskId = inspectionTaskRepository.findByPackageRevisionId(revisionId).orElseThrow().getId();
        String claim = mockMvc.perform(post("/api/v1/internal/software/inspector/tasks/claim")
                        .header("X-Inspector-Token", INSPECTOR_TOKEN)
                        .contentType("application/json")
                        .content("{\"workerId\":\"worker-a\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = extractJsonString(claim, "attemptToken");
        mockMvc.perform(post("/api/v1/internal/software/inspector/tasks/" + taskId + "/result")
                        .header("X-Inspector-Token", INSPECTOR_TOKEN)
                        .contentType("application/json")
                        .content(resultBody("worker-a", token, outcome, errorCode, rawFields)))
                .andExpect(status().isOk());
        return taskId;
    }

    /** Claims with a shifted clock (backoff bypass); returns the fresh attempt token. */
    private String claimViaServiceShifted(long taskId, int attempt) {
        // Shift past any pending backoff: claim N minutes after creation.
        Instant shifted = Instant.now().plusSeconds(attempt * 600L);
        // the task row was created with real timestamps; shift is only used to
        // pass next_attempt_not_before, so re-set it directly for determinism
        jdbcTemplate.update("UPDATE software_inspection_task SET next_attempt_not_before = NULL WHERE id = ?",
                taskId);
        var claimed = inspectionTaskService.claim("worker-a", shifted);
        assertThat(claimed).isNotNull();
        assertThat(claimed.attemptNo()).isEqualTo(attempt);
        return claimed.attemptToken();
    }

    private Long completeUpload(Long releaseId, String fileName, String format) {
        return completeUpload(releaseId, fileName, format, PAYLOAD);
    }

    private Long completeUpload(Long releaseId, String fileName, String format, byte[] payload) {
        SoftwareUploadSessionResponse session = uploadAppService.createSession(
                new SoftwareUploadSessionCreateRequest(releaseId, fileName, format,
                        "WINDOWS", "X86_64", null, null, "1.0.0.0", payload.length),
                "admin-1");
        uploadAppService.putChunk(session.id(), 1, payload.length,
                new java.io.ByteArrayInputStream(payload), "admin-1");
        var completion = uploadAppService.complete(session.id(), "admin-1");
        return completion.packageRevisionId();
    }

    private static String resultBody(String workerId, String token, String outcome,
                                     String errorCode, String rawFields) {
        StringBuilder sb = new StringBuilder("{\"workerId\":\"").append(workerId)
                .append("\",\"attemptToken\":\"").append(token)
                .append("\",\"outcome\":\"").append(outcome).append("\"");
        if (errorCode != null) {
            sb.append(",\"errorCode\":\"").append(errorCode).append("\"");
        }
        if (rawFields != null) {
            sb.append(",\"rawVersionFields\":").append(rawFields);
            sb.append(",\"parserName\":\"msiinfo\",\"parserVersion\":\"0.100\"");
        }
        return sb.append("}").toString();
    }

    private static String extractJsonString(String json, String field) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + field + "\":\"([^\"]+)\"")
                .matcher(json);
        assertThat(matcher.find()).as("field %s in %s", field, json).isTrue();
        return matcher.group(1);
    }

    private void seedPolicyAndAccount() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM software_inspection_attempt");
            jdbcTemplate.update("DELETE FROM software_inspection_task");
            jdbcTemplate.update("DELETE FROM software_upload_chunk");
            jdbcTemplate.update("DELETE FROM software_upload_session");
            jdbcTemplate.update("DELETE FROM software_download_session");
            jdbcTemplate.update(
                    "UPDATE software_storage_account SET used_bytes = 0, reserved_bytes = 0 WHERE id = 1");
            if (policyStateRepository.findSingleton().isEmpty()) {
                String config = SoftwarePolicyConfigJson.toJson(SoftwarePolicyConfig.defaults());
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
        try {
            Files.createDirectories(Path.of("./target/sw-inspection-base"));
        } catch (Exception ignored) {
        }
    }

    private Long createPublishedApp() {
        return transactionTemplate.execute(status -> {
            SoftwareApplication app = applicationRepository.save(
                    new SoftwareApplication("inspection-test-app-" + System.nanoTime(),
                            "Inspection Test App", "desc", null, null, "admin-1"));
            app.publish();
            return applicationRepository.save(app).getId();
        });
    }

    private Long createRelease(Long applicationId) {
        return transactionTemplate.execute(status ->
                releaseRepository.save(new SoftwareRelease(applicationId, "1.0.0", "notes",
                        "admin-1")).getId());
    }

    private static UsernamePasswordAuthenticationToken softwareAdmin() {
        return auth("admin-1", "ROLE_SOFTWARE_ADMIN");
    }

    private static UsernamePasswordAuthenticationToken regularUser() {
        return auth("user-1", "ROLE_USER");
    }

    private static UsernamePasswordAuthenticationToken auth(String userId, String role) {
        PlatformPrincipal principal = new PlatformPrincipal(userId, userId, userId + "@example.com", "",
                "github", java.util.Set.of(role.substring(5)));
        return new UsernamePasswordAuthenticationToken(principal, null,
                java.util.List.of(new SimpleGrantedAuthority(role)));
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
