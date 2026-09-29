package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rules for persistent static-inspection tasks. Workers claim a task in a
 * short transaction and receive an attempt token; parsing runs outside any
 * transaction; the result is written back with the token in a new
 * transaction. Stale reports — expired lease, wrong token, wrong worker — are
 * rejected. Only recoverable infrastructure failures consume the automatic
 * retry budget; basic validation failures are terminal until an administrator
 * triggers a controlled retry with its own budget. Inspection results never
 * override human-confirmed metadata.
 */
@Service
public class SoftwareInspectionTaskService {

    /** Maximum administrator-triggered retries per task, separate from the auto budget. */
    public static final int MAX_MANUAL_RETRIES = 3;

    /** Lease length granted at claim and extended by heartbeats. */
    static final Duration LEASE_DURATION = Duration.ofSeconds(120);

    private static final Duration RETRY_BACKOFF_STEP = Duration.ofSeconds(30);
    private static final Duration RETRY_BACKOFF_MAX = Duration.ofSeconds(300);

    private final SoftwareInspectionTaskRepository taskRepository;
    private final SoftwareInspectionAttemptRepository attemptRepository;
    private final SoftwarePolicyService policyService;
    private final SoftwarePackageService packageService;

    public SoftwareInspectionTaskService(SoftwareInspectionTaskRepository taskRepository,
                                         SoftwareInspectionAttemptRepository attemptRepository,
                                         SoftwarePolicyService policyService,
                                         SoftwarePackageService packageService) {
        this.taskRepository = taskRepository;
        this.attemptRepository = attemptRepository;
        this.policyService = policyService;
        this.packageService = packageService;
    }

    /**
     * Creates a task when an auto-inspected package revision lands. Manual
     * formats (ZIP/TAR archives) are filled in by administrators and never get
     * a task. Idempotent: a crash-recovery replay of the same revision reuses
     * the existing task.
     */
    @Transactional
    public void createForRevision(SoftwarePackageRevision revision) {
        if (!revision.getFormat().isAutoInspected()) {
            return;
        }
        if (taskRepository.findByPackageRevisionId(revision.getId()).isPresent()) {
            return;
        }
        SoftwarePolicySnapshot policy = policyService.requireActive();
        taskRepository.save(new SoftwareInspectionTask(
                revision.getId(), revision.getApplicationId(),
                policy.config().maxAutoInspectionAttempts(), policy,
                revision.getObjectKey(), revision.getFormat()));
    }

    /** Claims one due task for a worker; returns null when nothing is due. */
    @Transactional
    public ClaimedTask claim(String workerId, Instant now) {
        List<SoftwareInspectionTask> due = taskRepository.findClaimable(1);
        if (due.isEmpty()) {
            return null;
        }
        SoftwareInspectionTask task = due.getFirst();
        String token = UUID.randomUUID().toString();
        SoftwarePolicySnapshot policy = policyService.requireActive();
        task.claim(workerId, token, policy, now, now.plus(LEASE_DURATION));
        taskRepository.save(task);
        return new ClaimedTask(task.getId(), task.getPackageRevisionId(), task.getAttemptCount(), token,
                task.getObjectKey(), task.getFormat(), task.getPolicyRevision(),
                task.getTotalTimeoutSeconds(), task.getSubprocessTimeoutSeconds(),
                task.getSoftMemoryLimitBytes(), task.getMaxExtractedBytes(), task.getMaxExtractedEntries(),
                task.getLeaseExpiresAt());
    }

    /** Extends the lease of a running attempt. Stale calls are rejected. */
    @Transactional
    public Instant heartbeat(Long taskId, String workerId, String attemptToken) {
        SoftwareInspectionTask task = requireTask(taskId);
        if (!task.holdsLease(workerId, attemptToken)) {
            throw new DomainConflictException("error.software.inspection.leaseStale", taskId);
        }
        Instant leaseExpiresAt = Instant.now().plus(LEASE_DURATION);
        task.heartbeat(workerId, attemptToken, leaseExpiresAt);
        taskRepository.save(task);
        return leaseExpiresAt;
    }

    /** Records a worker's attempt result. Stale calls are rejected with 409. */
    @Transactional
    public SubmissionReceipt submitResult(Long taskId, String workerId, String attemptToken,
                                          ResultSubmission submission) {
        SoftwareInspectionTask task = requireTask(taskId);
        if (!task.holdsLease(workerId, attemptToken)) {
            throw new DomainConflictException("error.software.inspection.leaseStale", taskId);
        }
        int attemptNo = task.getAttemptCount();
        Instant now = Instant.now();
        requireErrorCodeFor(submission, now);

        if (submission.outcome() == SoftwareInspectionOutcome.SUCCEEDED) {
            String rawFields = requireJsonObject(submission.rawVersionFields());
            // Never overrides human-confirmed metadata: the revision write is
            // rejected once an administrator has confirmed the package.
            packageService.applyInspectionExtractedFields(task.getPackageRevisionId(), rawFields);
        }
        task.applyOutcome(submission.outcome(), now, now.plus(retryBackoff(attemptNo)));
        taskRepository.save(task);
        attemptRepository.save(new SoftwareInspectionAttempt(taskId, attemptNo,
                triggerFor(task), submission.outcome(), submission.errorCode(),
                submission.rawVersionFields(), submission.parserName(), submission.parserVersion(),
                submission.startedAt(), submission.finishedAt()));
        return new SubmissionReceipt(taskId, task.getStatus(), attemptNo);
    }

    /** Administrator-triggered controlled retry with its own budget. */
    @Transactional
    public SoftwareInspectionTask manualRetry(Long taskId) {
        SoftwareInspectionTask task = requireTask(taskId);
        if (!task.getStatus().isManuallyRetriable()) {
            throw new DomainConflictException("error.software.inspection.invalidState",
                    taskId, task.getStatus());
        }
        if (task.getManualRetryCount() >= MAX_MANUAL_RETRIES) {
            throw new DomainConflictException("error.software.inspection.retryBudgetExhausted", taskId);
        }
        task.manualRetry(Instant.now());
        return taskRepository.save(task);
    }

    /**
     * Reclaim pass for leases that expired without a result. When the auto
     * budget is spent the task is closed as EXHAUSTED and the lost attempt is
     * recorded; otherwise the next claim picks the task up again.
     */
    @Transactional
    public int reclaimExpiredLeases(Instant now, int limit) {
        List<SoftwareInspectionTask> expired = taskRepository.findExpiredLeases(limit);
        int exhausted = 0;
        for (SoftwareInspectionTask task : expired) {
            if (!task.exhaustedByExpiredLease()) {
                continue;
            }
            task.exhaustFromExpiredLease(now);
            taskRepository.save(task);
            attemptRepository.save(new SoftwareInspectionAttempt(task.getId(), task.getAttemptCount(),
                    triggerFor(task), SoftwareInspectionOutcome.INFRA_FAILED,
                    "error.software.inspection.leaseExpired", null, null, null, null, now));
            exhausted++;
        }
        return exhausted;
    }

    public List<SoftwareInspectionAttempt> attemptsOf(Long taskId) {
        return attemptRepository.findByTaskIdOrderById(requireTask(taskId).getId());
    }

    private void requireErrorCodeFor(ResultSubmission submission, Instant now) {
        if (submission.outcome() == SoftwareInspectionOutcome.SUCCEEDED) {
            return;
        }
        if (submission.outcome() != SoftwareInspectionOutcome.INFRA_FAILED
                && (submission.errorCode() == null || submission.errorCode().isBlank())) {
            throw new DomainBadRequestException("error.software.inspection.errorCodeRequired");
        }
    }

    private SoftwareInspectionTrigger triggerFor(SoftwareInspectionTask task) {
        return task.getManualRetryCount() > 0
                ? SoftwareInspectionTrigger.MANUAL : SoftwareInspectionTrigger.AUTO;
    }

    private Duration retryBackoff(int attemptNo) {
        Duration backoff = RETRY_BACKOFF_STEP.multipliedBy(Math.max(1, attemptNo));
        return backoff.compareTo(RETRY_BACKOFF_MAX) > 0 ? RETRY_BACKOFF_MAX : backoff;
    }

    private String requireJsonObject(String json) {
        if (json == null || json.isBlank()) {
            throw new DomainBadRequestException("error.software.inspection.rawFieldsInvalid");
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(json);
            if (node == null || !node.isObject()) {
                throw new DomainBadRequestException("error.software.inspection.rawFieldsInvalid");
            }
            return json;
        } catch (DomainBadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainBadRequestException("error.software.inspection.rawFieldsInvalid");
        }
    }

    /** Public lookup for app-layer orchestration; 404 when missing. */
    public SoftwareInspectionTask getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new DomainNotFoundException("error.software.inspection.taskNotFound", taskId));
    }

    private SoftwareInspectionTask requireTask(Long taskId) {
        return getTask(taskId);
    }

    /** A task handed to a worker, with the claim-time policy snapshot. */
    public record ClaimedTask(Long taskId,
                              Long packageRevisionId,
                              int attemptNo,
                              String attemptToken,
                              String objectKey,
                              SoftwarePackageFormat format,
                              long policyRevision,
                              long totalTimeoutSeconds,
                              long subprocessTimeoutSeconds,
                              long softMemoryLimitBytes,
                              long maxExtractedBytes,
                              int maxExtractedEntries,
                              Instant leaseExpiresAt) {
    }

    /** A worker's reported result for one attempt. */
    public record ResultSubmission(SoftwareInspectionOutcome outcome,
                                   String errorCode,
                                   String rawVersionFields,
                                   String parserName,
                                   String parserVersion,
                                   Instant startedAt,
                                   Instant finishedAt) {
    }

    public record SubmissionReceipt(Long taskId, SoftwareInspectionTaskStatus status, int attemptNo) {
    }
}
