package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.software.SoftwareInspectionAttempt;
import com.iflytek.skillhub.domain.software.SoftwareInspectionTask;
import com.iflytek.skillhub.domain.software.SoftwareInspectionTaskService;
import com.iflytek.skillhub.domain.software.SoftwareInspectionTaskStatus;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.domain.software.SoftwarePackageService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.software.SoftwareInspectionAttemptResponse;
import com.iflytek.skillhub.dto.software.SoftwareInspectionDetailResponse;
import com.iflytek.skillhub.dto.software.SoftwareInspectionSummaryResponse;
import com.iflytek.skillhub.repository.SoftwareInspectionQueryRepository;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-side orchestration for the inspection console: listing, controlled
 * retries, and metadata confirmation with manual fill-in. Every state
 * transition is audited in the same transaction.
 */
@Service
public class SoftwareInspectionAppService {

    private final SoftwareInspectionQueryRepository queryRepository;
    private final SoftwareInspectionTaskService inspectionTaskService;
    private final SoftwarePackageService packageService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final ObjectMapper objectMapper;

    public SoftwareInspectionAppService(SoftwareInspectionQueryRepository queryRepository,
                                        SoftwareInspectionTaskService inspectionTaskService,
                                        SoftwarePackageService packageService,
                                        AuditLogService auditLogService,
                                        RequestIdAccessor requestIdAccessor,
                                        ObjectMapper objectMapper) {
        this.queryRepository = queryRepository;
        this.inspectionTaskService = inspectionTaskService;
        this.packageService = packageService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
        this.objectMapper = objectMapper;
    }

    public Page<SoftwareInspectionSummaryResponse> list(SoftwareInspectionTaskStatus status, Pageable pageable) {
        return queryRepository.search(status, pageable);
    }

    /** Fresh read-model projection of one task; 404 when missing. */
    @Transactional(readOnly = true)
    public SoftwareInspectionSummaryResponse summary(Long taskId) {
        SoftwareInspectionSummaryResponse summary = queryRepository.findByTaskId(taskId);
        if (summary == null) {
            throw new DomainNotFoundException("error.software.inspection.taskNotFound", taskId);
        }
        return summary;
    }

    @Transactional(readOnly = true)
    public SoftwareInspectionDetailResponse detail(Long taskId) {
        SoftwareInspectionSummaryResponse summary = queryRepository.findByTaskId(taskId);
        if (summary == null) {
            throw new DomainNotFoundException("error.software.inspection.taskNotFound", taskId);
        }
        var attempts = inspectionTaskService.attemptsOf(taskId).stream()
                .map(this::toAttemptResponse)
                .toList();
        return new SoftwareInspectionDetailResponse(summary, attempts);
    }

    @Transactional
    public SoftwareInspectionTask retry(Long taskId, String actorId, AuditRequestContext auditContext) {
        SoftwareInspectionTask task = inspectionTaskService.manualRetry(taskId);
        recordAudit("software.inspection.retry", "SOFTWARE_INSPECTION", taskId, actorId, auditContext, null);
        return task;
    }

    /**
     * Confirms the package metadata behind the task, optionally filling in
     * manually supplied raw fields. Basic validation failures cannot be
     * confirmed away — the domain rejects the whole action.
     */
    @Transactional
    public SoftwarePackageRevision confirmMetadata(Long taskId, JsonNode rawVersionFields,
                                                   String actorId, AuditRequestContext auditContext) {
        SoftwareInspectionTask task = requireTask(taskId);
        String fillInJson = rawVersionFields == null ? null : serializeFields(rawVersionFields);
        SoftwarePackageRevision revision = packageService.confirm(task.getPackageRevisionId(), actorId, fillInJson);
        recordAudit("software.inspection.confirmMetadata", "SOFTWARE_INSPECTION", taskId, actorId, auditContext,
                fillInJson == null ? null : jsonField("filled", true));
        return revision;
    }

    private SoftwareInspectionTask requireTask(Long taskId) {
        return inspectionTaskService.getTask(taskId);
    }

    private String serializeFields(JsonNode rawVersionFields) {
        if (!rawVersionFields.isObject()) {
            throw new DomainBadRequestException("error.software.inspection.rawFieldsInvalid");
        }
        try {
            return objectMapper.writeValueAsString(rawVersionFields);
        } catch (Exception ex) {
            throw new DomainBadRequestException("error.software.inspection.rawFieldsInvalid");
        }
    }

    private SoftwareInspectionAttemptResponse toAttemptResponse(SoftwareInspectionAttempt attempt) {
        return new SoftwareInspectionAttemptResponse(attempt.getAttemptNo(),
                attempt.getTrigger().name(), attempt.getOutcome().name(), attempt.getErrorCode(),
                parseJson(attempt.getRawVersionFields()), attempt.getParserName(), attempt.getParserVersion(),
                attempt.getStartedAt(), attempt.getFinishedAt());
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private String jsonField(String key, Object value) {
        try {
            return objectMapper.writeValueAsString(objectMapper.createObjectNode().putPOJO(key, value));
        } catch (Exception ex) {
            return null;
        }
    }

    private void recordAudit(String action, String targetType, Long targetId, String actorId,
                             AuditRequestContext auditContext, String detailJson) {
        auditLogService.record(
                actorId,
                action,
                targetType,
                targetId,
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                detailJson
        );
    }
}
