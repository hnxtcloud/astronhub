package com.iflytek.skillhub.controller.internal;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.software.SoftwareInspectionOutcome;
import com.iflytek.skillhub.domain.software.SoftwareInspectionTask;
import com.iflytek.skillhub.domain.software.SoftwareInspectionTaskService;
import com.iflytek.skillhub.dto.software.SoftwareInspectorClaimResponse;
import com.iflytek.skillhub.dto.software.SoftwareInspectorHeartbeatRequest;
import com.iflytek.skillhub.dto.software.SoftwareInspectorResultRequest;
import com.iflytek.skillhub.storage.software.SoftwareTransferStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.InputStream;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal inspector task API. Reachable only with the deployment-scoped
 * inspector token enforced by {@code SoftwareInspectorAuthFilter}; no user
 * session, no admin role, no API token can reach it. The worker claims a
 * task, streams the immutable package bytes by task id, and reports the
 * attempt result with its attempt token — stale reports are rejected with 409.
 * Responses are plain JSON envelopes: this API is machine-to-machine only.
 */
@RestController
@RequestMapping("/api/v1/internal/software/inspector")
public class SoftwareInspectorInternalController {

    private final SoftwareInspectionTaskService inspectionTaskService;
    private final SoftwareTransferStorage transferStorage;

    public SoftwareInspectorInternalController(SoftwareInspectionTaskService inspectionTaskService,
                                               SoftwareTransferStorage transferStorage) {
        this.inspectionTaskService = inspectionTaskService;
        this.transferStorage = transferStorage;
    }

    @PostMapping("/tasks/claim")
    public SoftwareInspectorClaimResponse claim(@RequestBody Map<String, String> body) {
        String workerId = requireWorkerId(body);
        SoftwareInspectionTaskService.ClaimedTask claimed =
                inspectionTaskService.claim(workerId, java.time.Instant.now());
        if (claimed == null) {
            return SoftwareInspectorClaimResponse.empty();
        }
        return new SoftwareInspectorClaimResponse(true, claimed.taskId(), claimed.packageRevisionId(),
                claimed.attemptNo(), claimed.attemptToken(), claimed.objectKey(), claimed.format().name(),
                claimed.policyRevision(), claimed.totalTimeoutSeconds(), claimed.subprocessTimeoutSeconds(),
                claimed.softMemoryLimitBytes(), claimed.maxExtractedBytes(), claimed.maxExtractedEntries(),
                claimed.leaseExpiresAt());
    }

    @PostMapping("/tasks/{taskId}/heartbeat")
    public Map<String, Object> heartbeat(@PathVariable Long taskId,
                                         @Valid @RequestBody SoftwareInspectorHeartbeatRequest request) {
        java.time.Instant leaseExpiresAt =
                inspectionTaskService.heartbeat(taskId, request.workerId(), request.attemptToken());
        return Map.of("taskId", taskId, "leaseExpiresAt", leaseExpiresAt.toString());
    }

    @PostMapping("/tasks/{taskId}/result")
    public Map<String, Object> submitResult(@PathVariable Long taskId,
                                            @Valid @RequestBody SoftwareInspectorResultRequest request) {
        SoftwareInspectionTaskService.SubmissionReceipt receipt = inspectionTaskService.submitResult(
                taskId, request.workerId(), request.attemptToken(),
                new SoftwareInspectionTaskService.ResultSubmission(
                        parseOutcome(request.outcome()), request.errorCode(),
                        serializeRawFields(request.rawVersionFields()), request.parserName(),
                        request.parserVersion(), request.startedAt(), request.finishedAt()));
        return Map.of("taskId", receipt.taskId(), "status", receipt.status().name(),
                "attemptNo", receipt.attemptNo());
    }

    /** Streams the immutable package bytes for the claimed task. */
    @GetMapping("/tasks/{taskId}/content")
    public void downloadContent(@PathVariable Long taskId, HttpServletRequest request,
                                HttpServletResponse response) throws Exception {
        SoftwareInspectionTask task = inspectionTaskService.getTask(taskId);
        long size = transferStorage.objectSize(task.getObjectKey());
        response.setContentType("application/octet-stream");
        response.setContentLengthLong(size);
        try (InputStream in = transferStorage.readRange(task.getObjectKey(), 0, size);
             java.io.OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    private String requireWorkerId(Map<String, String> body) {
        String workerId = body == null ? null : body.get("workerId");
        if (workerId == null || workerId.isBlank()) {
            throw new DomainBadRequestException("error.software.inspection.workerIdRequired");
        }
        return workerId;
    }

    private SoftwareInspectionOutcome parseOutcome(String outcome) {
        try {
            return SoftwareInspectionOutcome.valueOf(outcome);
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.software.inspection.outcomeInvalid", outcome);
        }
    }

    private String serializeRawFields(com.fasterxml.jackson.databind.JsonNode rawVersionFields) {
        if (rawVersionFields == null || rawVersionFields.isNull()) {
            return null;
        }
        if (!rawVersionFields.isObject()) {
            throw new DomainBadRequestException("error.software.inspection.rawFieldsInvalid");
        }
        try {
            return rawVersionFields.toString();
        } catch (Exception ex) {
            throw new DomainBadRequestException("error.software.inspection.rawFieldsInvalid");
        }
    }
}
