package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.software.SoftwarePolicyConfig;
import com.iflytek.skillhub.domain.software.SoftwarePolicyDeploymentLimits;
import com.iflytek.skillhub.domain.software.SoftwarePolicyRevision;
import com.iflytek.skillhub.domain.software.SoftwarePolicyService;
import com.iflytek.skillhub.domain.software.SoftwarePolicySnapshot;
import com.iflytek.skillhub.domain.software.SoftwarePolicyConfigJson;
import com.iflytek.skillhub.dto.software.SoftwareDeploymentLimitsDto;
import com.iflytek.skillhub.dto.software.SoftwarePolicyConfigDto;
import com.iflytek.skillhub.dto.software.SoftwarePolicyRevisionResponse;
import com.iflytek.skillhub.dto.software.SoftwareSettingsResponse;
import com.iflytek.skillhub.dto.software.SoftwareSettingsValidationResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings use cases over the versioned software runtime policy. Writes are
 * SUPER_ADMIN only and audited with field-level before/after values; software
 * administrators get read-only access to the effective configuration.
 */
@Service
public class SoftwareSettingsAppService {

    private static final int HISTORY_PAGE_SIZE = 50;

    private final SoftwarePolicyService policyService;
    private final SoftwarePolicyDeploymentLimits deploymentLimits;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;

    public SoftwareSettingsAppService(SoftwarePolicyService policyService,
                                      SoftwarePolicyDeploymentLimits deploymentLimits,
                                      AuditLogService auditLogService,
                                      RequestIdAccessor requestIdAccessor) {
        this.policyService = policyService;
        this.deploymentLimits = deploymentLimits;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    /** Reads the authoritative active revision; returns null when unseeded. */
    @Transactional(readOnly = true)
    public SoftwareSettingsResponse getActive() {
        SoftwarePolicySnapshot snapshot = policyService.findActive();
        if (snapshot == null) {
            return null;
        }
        return toResponse(snapshot);
    }

    /**
     * Validates and persists a new revision with optimistic concurrency; the
     * caller's expectedRevision mismatch yields 409 before any write happens.
     */
    @Transactional
    public SoftwareSettingsResponse update(SoftwarePolicyConfigDto request,
                                           Long expectedRevision,
                                           String changeReason,
                                           String actorId,
                                           AuditRequestContext auditContext) {
        SoftwarePolicySnapshot before = policyService.requireActive();
        SoftwarePolicySnapshot after = policyService.update(
                expectedRevision, toConfig(request), actorId, changeReason);
        auditLogService.record(
                actorId,
                "software.policy.update",
                "SOFTWARE_POLICY",
                after.revision(),
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                fieldDiff(before, after)
        );
        return toResponse(after);
    }

    @Transactional(readOnly = true)
    public SoftwareSettingsValidationResponse validate(SoftwarePolicyConfigDto candidate) {
        var violations = policyService.validate(toConfig(candidate));
        return new SoftwareSettingsValidationResponse(
                violations.isEmpty(),
                violations.stream()
                        .map(v -> new SoftwareSettingsValidationResponse.FieldViolation(
                                v.field(), v.messageCode(), v.messageArgs()))
                        .toList()
        );
    }

    /** Rollback creates a new audited revision from a historical one's values. */
    @Transactional
    public SoftwareSettingsResponse rollback(Long fromRevision, String changeReason, String actorId,
                                             AuditRequestContext auditContext) {
        SoftwarePolicySnapshot before = policyService.requireActive();
        SoftwarePolicySnapshot after = policyService.rollback(fromRevision, actorId, changeReason);
        auditLogService.record(
                actorId,
                "software.policy.rollback",
                "SOFTWARE_POLICY",
                after.revision(),
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                fieldDiff(before, after)
        );
        return toResponse(after);
    }

    @Transactional(readOnly = true)
    public Page<SoftwarePolicyRevisionResponse> history(int page) {
        Page<SoftwarePolicyRevision> revisions = policyService.history(
                PageRequest.of(Math.max(page, 0), HISTORY_PAGE_SIZE));
        return revisions.map(this::toRevisionResponse);
    }

    private SoftwareSettingsResponse toResponse(SoftwarePolicySnapshot snapshot) {
        return new SoftwareSettingsResponse(
                snapshot.revision(),
                snapshot.schemaVersion(),
                toDto(snapshot.config()),
                new SoftwareDeploymentLimitsDto(
                        deploymentLimits.proxyMaxBodyBytes(),
                        deploymentLimits.workerMemoryHardLimitBytes(),
                        deploymentLimits.workerMemoryReservedBytes(),
                        deploymentLimits.workerAvailableMemoryBytes()
                ),
                snapshot.changedBy(),
                snapshot.changedAt()
        );
    }

    private SoftwarePolicyRevisionResponse toRevisionResponse(SoftwarePolicyRevision revision) {
        return new SoftwarePolicyRevisionResponse(
                revision.getRevision(),
                revision.getSchemaVersion(),
                toDto(SoftwarePolicyConfigJson.fromJson(revision.getConfig())),
                revision.getChangedBy(),
                revision.getChangeReason(),
                revision.getCreatedAt()
        );
    }

    private SoftwarePolicyConfig toConfig(SoftwarePolicyConfigDto dto) {
        return new SoftwarePolicyConfig(
                dto.maxPackageBytes(), dto.totalQuotaBytes(), dto.maxActiveUploadsPerAdmin(),
                dto.chunkSizeBytes(), dto.uploadIdleTimeoutMinutes(), dto.failedTempRetentionHours(),
                dto.cleanupScanIntervalMinutes(), dto.maxConcurrentInspectionsPerWorker(),
                dto.inspectionTotalTimeoutSeconds(), dto.inspectionSubprocessTimeoutSeconds(),
                dto.inspectionSoftMemoryLimitBytes(), dto.inspectionMaxExtractedBytes(),
                dto.inspectionMaxExtractedEntries(), dto.maxAutoInspectionAttempts(),
                dto.downloadSessionIdleTimeoutMinutes()
        );
    }

    private SoftwarePolicyConfigDto toDto(SoftwarePolicyConfig config) {
        return new SoftwarePolicyConfigDto(
                config.maxPackageBytes(), config.totalQuotaBytes(), config.maxActiveUploadsPerAdmin(),
                config.chunkSizeBytes(), config.uploadIdleTimeoutMinutes(), config.failedTempRetentionHours(),
                config.cleanupScanIntervalMinutes(), config.maxConcurrentInspectionsPerWorker(),
                config.inspectionTotalTimeoutSeconds(), config.inspectionSubprocessTimeoutSeconds(),
                config.inspectionSoftMemoryLimitBytes(), config.inspectionMaxExtractedBytes(),
                config.inspectionMaxExtractedEntries(), config.maxAutoInspectionAttempts(),
                config.downloadSessionIdleTimeoutMinutes()
        );
    }

    /** Field-level before/after diff persisted with the audit entry. */
    private String fieldDiff(SoftwarePolicySnapshot before, SoftwarePolicySnapshot after) {
        try {
            Map<String, Object> beforeFields = SoftwarePolicyConfigJson.toFieldMap(
                    SoftwarePolicyConfigJson.toJson(before.config()));
            Map<String, Object> afterFields = SoftwarePolicyConfigJson.toFieldMap(
                    SoftwarePolicyConfigJson.toJson(after.config()));
            Map<String, Object> changed = new java.util.LinkedHashMap<>();
            for (String key : beforeFields.keySet()) {
                Object oldValue = beforeFields.get(key);
                Object newValue = afterFields.get(key);
                if (!java.util.Objects.equals(oldValue, newValue)) {
                    Map<String, Object> pair = new java.util.LinkedHashMap<>();
                    pair.put("before", oldValue);
                    pair.put("after", newValue);
                    changed.put(key, pair);
                }
            }
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("fromRevision", before.revision());
            detail.put("toRevision", after.revision());
            detail.put("changedFields", changed);
            return new ObjectMapper().writeValueAsString(detail);
        } catch (Exception ex) {
            return null;
        }
    }
}
