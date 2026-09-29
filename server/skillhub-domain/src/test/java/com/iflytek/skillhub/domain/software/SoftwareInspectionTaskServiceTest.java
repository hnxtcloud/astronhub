package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Rules of the persistent inspection task lifecycle: claim/lease/attempt-token
 * semantics, terminal versus manual-fill outcomes, retry budgets, and stale
 * result rejection.
 */
class SoftwareInspectionTaskServiceTest {

    private SoftwareInspectionTaskRepository taskRepository;
    private SoftwareInspectionAttemptRepository attemptRepository;
    private SoftwarePolicyService policyService;
    private SoftwarePackageService packageService;
    private SoftwareInspectionTaskService service;

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final SoftwarePolicySnapshot POLICY = new SoftwarePolicySnapshot(
            1, 1, SoftwarePolicyConfig.defaults(), "admin", NOW);

    @BeforeEach
    void setUp() {
        taskRepository = Mockito.mock(SoftwareInspectionTaskRepository.class);
        attemptRepository = Mockito.mock(SoftwareInspectionAttemptRepository.class);
        policyService = Mockito.mock(SoftwarePolicyService.class);
        packageService = Mockito.mock(SoftwarePackageService.class);
        when(policyService.findActive()).thenReturn(POLICY);
        when(policyService.requireActive()).thenReturn(POLICY);
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new SoftwareInspectionTaskService(
                taskRepository, attemptRepository, policyService, packageService);
    }

    @Test
    void createForRevision_skipsManualFormatsAndExistingTasks() {
        service.createForRevision(revision(10L, SoftwarePackageFormat.ZIP));
        verify(taskRepository, never()).save(any());

        when(taskRepository.findByPackageRevisionId(11L))
                .thenReturn(Optional.of(new SoftwareInspectionTask(11L, 1L, 3, POLICY, "k", SoftwarePackageFormat.MSI)));
        service.createForRevision(revision(11L, SoftwarePackageFormat.MSI));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createForRevision_snapshotsActivePolicy() {
        service.createForRevision(revision(11L, SoftwarePackageFormat.MSI));

        var saved = captureSavedTask();
        assertThat(saved.getStatus()).isEqualTo(SoftwareInspectionTaskStatus.PENDING);
        assertThat(saved.getMaxAttempts()).isEqualTo(3);
        assertThat(saved.getPolicyRevision()).isEqualTo(1L);
        assertThat(saved.getTotalTimeoutSeconds()).isEqualTo(15 * 60);
        assertThat(saved.getSubprocessTimeoutSeconds()).isEqualTo(60);
        assertThat(saved.getMaxExtractedEntries()).isEqualTo(10_000);
    }

    @Test
    void claim_returnsNullWhenNothingDue() {
        when(taskRepository.findClaimable(1)).thenReturn(List.of());
        assertThat(service.claim("worker-1", NOW)).isNull();
    }

    @Test
    void claim_allocatesTokenLeaseAndRefreshesPolicy() {
        SoftwareInspectionTask task = pendingTask(7L);
        when(taskRepository.findClaimable(1)).thenReturn(List.of(task));

        SoftwareInspectionTaskService.ClaimedTask claimed = service.claim("worker-1", NOW);

        assertThat(claimed.taskId()).isEqualTo(7L);
        assertThat(claimed.attemptNo()).isEqualTo(1);
        assertThat(claimed.attemptToken()).isNotBlank();
        assertThat(claimed.leaseExpiresAt()).isAfter(NOW);
        assertThat(task.getStatus()).isEqualTo(SoftwareInspectionTaskStatus.LEASED);
        assertThat(task.getLeaseOwner()).isEqualTo("worker-1");
        assertThat(task.getAttemptCount()).isEqualTo(1);
        // stale snapshot from creation is refreshed at claim time
        assertThat(task.getPolicyRevision()).isEqualTo(1L);
    }

    @Test
    void heartbeat_rejectsStaleTokenOrWrongWorker() {
        SoftwareInspectionTask task = claimedTask(7L, "worker-1", "token-1");
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.heartbeat(7L, "worker-1", "wrong"))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("error.software.inspection.leaseStale");
        assertThatThrownBy(() -> service.heartbeat(7L, "worker-2", "token-1"))
                .hasMessageContaining("error.software.inspection.leaseStale");
    }

    @Test
    void heartbeat_extendsLeaseForHolder() {
        SoftwareInspectionTask task = claimedTask(7L, "worker-1", "token-1");
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        Instant newExpiry = service.heartbeat(7L, "worker-1", "token-1");

        assertThat(task.getLeaseExpiresAt()).isEqualTo(newExpiry);
    }

    @Test
    void submitResult_rejectsStaleLeaseWithoutRecording() {
        SoftwareInspectionTask task = claimedTask(7L, "worker-1", "token-1");
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.submitResult(7L, "worker-1", "expired-token",
                succeeded("{}")))
                .hasMessageContaining("error.software.inspection.leaseStale");
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void submitResult_succeededWritesFieldsAndRecordsAttempt() {
        SoftwareInspectionTask task = claimedTask(7L, "worker-1", "token-1");
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        var receipt = service.submitResult(7L, "worker-1", "token-1",
                new SoftwareInspectionTaskService.ResultSubmission(
                        SoftwareInspectionOutcome.SUCCEEDED, null, "{\"a\":1}",
                        "msiinfo", "1.0", NOW, NOW.plusSeconds(5)));

        assertThat(receipt.status()).isEqualTo(SoftwareInspectionTaskStatus.SUCCEEDED);
        assertThat(receipt.attemptNo()).isEqualTo(1);
        verify(packageService).applyInspectionExtractedFields(10L, "{\"a\":1}");
        var attempt = captureSavedAttempt();
        assertThat(attempt.getOutcome()).isEqualTo(SoftwareInspectionOutcome.SUCCEEDED);
        assertThat(attempt.getAttemptNo()).isEqualTo(1);
        assertThat(task.getStatus()).isEqualTo(SoftwareInspectionTaskStatus.SUCCEEDED);
        assertThat(task.getAttemptToken()).isNull();
    }

    @Test
    void submitResult_succeededRequiresJsonObject() {
        SoftwareInspectionTask task = claimedTask(7L, "worker-1", "token-1");
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.submitResult(7L, "worker-1", "token-1", succeeded("[1,2]")))
                .hasMessageContaining("error.software.inspection.rawFieldsInvalid");
        assertThatThrownBy(() -> service.submitResult(7L, "worker-1", "token-1", succeeded(null)))
                .hasMessageContaining("error.software.inspection.rawFieldsInvalid");
    }

    @Test
    void submitResult_basicFailedIsTerminalAndRequiresErrorCode() {
        SoftwareInspectionTask task = claimedTask(7L, "worker-1", "token-1");
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.submitResult(7L, "worker-1", "token-1",
                new SoftwareInspectionTaskService.ResultSubmission(
                        SoftwareInspectionOutcome.BASIC_FAILED, " ", null, null, null, null, null)))
                .hasMessageContaining("error.software.inspection.errorCodeRequired");

        service.submitResult(7L, "worker-1", "token-1",
                new SoftwareInspectionTaskService.ResultSubmission(
                        SoftwareInspectionOutcome.BASIC_FAILED, "validation.traversal", null, null, null, null, null));
        assertThat(task.getStatus()).isEqualTo(SoftwareInspectionTaskStatus.BASIC_FAILED);
    }

    @Test
    void submitResult_metadataFailedLandsInManualFillState() {
        SoftwareInspectionTask task = claimedTask(7L, "worker-1", "token-1");
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        service.submitResult(7L, "worker-1", "token-1",
                new SoftwareInspectionTaskService.ResultSubmission(
                        SoftwareInspectionOutcome.METADATA_FAILED, "parser.missing", null, "msiinfo", "1", null, null));

        assertThat(task.getStatus()).isEqualTo(SoftwareInspectionTaskStatus.METADATA_FAILED);
    }

    @Test
    void submitResult_infraFailedSchedulesBackoffThenExhausts() {
        SoftwareInspectionTask task = claimedTask(7L, "worker-1", "token-1");
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        service.submitResult(7L, "worker-1", "token-1",
                new SoftwareInspectionTaskService.ResultSubmission(
                        SoftwareInspectionOutcome.INFRA_FAILED, "worker.crash", null, null, null, null, null));
        assertThat(task.getStatus()).isEqualTo(SoftwareInspectionTaskStatus.PENDING);
        // the service anchors the backoff on wall-clock result time
        assertThat(task.getNextAttemptNotBefore())
                .isCloseTo(Instant.now().plusSeconds(30),
                        org.assertj.core.api.Assertions.within(15, java.time.temporal.ChronoUnit.SECONDS));

        // second failure at attempt 2 still within budget
        when(taskRepository.findClaimable(1)).thenReturn(List.of(task));
        service.claim("worker-1", NOW.plusSeconds(60));
        service.submitResult(7L, "worker-1", task.getAttemptToken(),
                new SoftwareInspectionTaskService.ResultSubmission(
                        SoftwareInspectionOutcome.INFRA_FAILED, "worker.crash", null, null, null, null, null));
        assertThat(task.getStatus()).isEqualTo(SoftwareInspectionTaskStatus.PENDING);

        // attempt 3 (== max) exhausts the budget
        when(taskRepository.findClaimable(1)).thenReturn(List.of(task));
        service.claim("worker-1", NOW.plusSeconds(120));
        service.submitResult(7L, "worker-1", task.getAttemptToken(),
                new SoftwareInspectionTaskService.ResultSubmission(
                        SoftwareInspectionOutcome.INFRA_FAILED, "worker.crash", null, null, null, null, null));
        assertThat(task.getStatus()).isEqualTo(SoftwareInspectionTaskStatus.EXHAUSTED);
    }

    @Test
    void manualRetry_requiresFailedStateAndOwnBudget() {
        SoftwareInspectionTask succeeded = claimedTask(7L, "worker-1", "t");
        succeeded.applyOutcome(SoftwareInspectionOutcome.SUCCEEDED, NOW, null);
        when(taskRepository.findById(7L)).thenReturn(Optional.of(succeeded));
        assertThatThrownBy(() -> service.manualRetry(7L))
                .hasMessageContaining("error.software.inspection.invalidState");

        SoftwareInspectionTask failed = new SoftwareInspectionTask(10L, 1L, 3, POLICY, "k", SoftwarePackageFormat.MSI);
        failed.applyOutcome(SoftwareInspectionOutcome.BASIC_FAILED, NOW, null);
        when(taskRepository.findById(8L)).thenReturn(Optional.of(failed));
        service.manualRetry(8L);
        assertThat(failed.getStatus()).isEqualTo(SoftwareInspectionTaskStatus.PENDING);
        assertThat(failed.getManualRetryCount()).isEqualTo(1);
        assertThat(failed.getAttemptCount()).isZero();
        assertThat(failed.getNextAttemptNotBefore()).isNull();

        failed.manualRetry(NOW);
        failed.manualRetry(NOW);
        assertThatThrownBy(() -> service.manualRetry(8L))
                .hasMessageContaining("error.software.inspection.retryBudgetExhausted");
    }

    @Test
    void reclaimExpiredLeases_exhaustsOnlySpentTasks() {
        SoftwareInspectionTask spent = new SoftwareInspectionTask(10L, 1L, 3, POLICY, "k", SoftwarePackageFormat.MSI);
        spent.claim("worker-1", "t", POLICY, NOW.minusSeconds(600), NOW.minusSeconds(480));
        spent.applyOutcome(infraFailure(), NOW.minusSeconds(400), null);
        spent.claim("worker-1", "t2", POLICY, NOW.minusSeconds(300), NOW.minusSeconds(180));
        spent.applyOutcome(infraFailure(), NOW.minusSeconds(100), null);
        spent.claim("worker-1", "t3", POLICY, NOW.minusSeconds(90), NOW.minusSeconds(30));
        assignId(spent, 21L);

        SoftwareInspectionTask withBudget = new SoftwareInspectionTask(11L, 1L, 3, POLICY, "k", SoftwarePackageFormat.MSI);
        withBudget.claim("worker-1", "t", POLICY, NOW.minusSeconds(600), NOW.minusSeconds(480));
        assignId(withBudget, 22L);

        when(taskRepository.findExpiredLeases(20)).thenReturn(List.of(spent, withBudget));

        int exhausted = service.reclaimExpiredLeases(NOW, 20);

        assertThat(exhausted).isEqualTo(1);
        assertThat(spent.getStatus()).isEqualTo(SoftwareInspectionTaskStatus.EXHAUSTED);
        assertThat(withBudget.getStatus()).isEqualTo(SoftwareInspectionTaskStatus.LEASED);
        var lost = captureSavedAttempt();
        assertThat(lost.getTaskId()).isEqualTo(21L);
        assertThat(lost.getOutcome()).isEqualTo(SoftwareInspectionOutcome.INFRA_FAILED);
    }

    private SoftwareInspectionOutcome infraFailure() {
        return SoftwareInspectionOutcome.INFRA_FAILED;
    }

    private SoftwareInspectionTaskService.ResultSubmission succeeded(String rawFields) {
        return new SoftwareInspectionTaskService.ResultSubmission(
                SoftwareInspectionOutcome.SUCCEEDED, null, rawFields, "msiinfo", "1.0", NOW, NOW);
    }

    private SoftwareInspectionTask pendingTask(long id) {
        SoftwareInspectionTask task = new SoftwareInspectionTask(
                10L, 1L, 3,
                new SoftwarePolicySnapshot(0, 1, SoftwarePolicyConfig.defaults(), "admin", NOW),
                "software/packages/x/i.msi", SoftwarePackageFormat.MSI);
        assignId(task, id);
        return task;
    }

    private SoftwareInspectionTask claimedTask(long id, String workerId, String token) {
        SoftwareInspectionTask task = pendingTask(id);
        task.claim(workerId, token, POLICY, NOW, NOW.plusSeconds(120));
        return task;
    }

    private SoftwarePackageRevision revision(long id, SoftwarePackageFormat format) {
        SoftwarePackageRevision revision = new SoftwarePackageRevision(
                1L, 1L, 1, SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                format, "1.0.0.0", null, "installer.msi", "a".repeat(64), 1024,
                "software/packages/x/i.msi", "admin-1");
        assignId(revision, id);
        return revision;
    }

    private SoftwareInspectionTask captureSavedTask() {
        var saved = org.mockito.ArgumentCaptor.forClass(SoftwareInspectionTask.class);
        verify(taskRepository).save(saved.capture());
        return saved.getValue();
    }

    private SoftwareInspectionAttempt captureSavedAttempt() {
        var saved = org.mockito.ArgumentCaptor.forClass(SoftwareInspectionAttempt.class);
        Mockito.verify(attemptRepository, Mockito.atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    private static void assignId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
