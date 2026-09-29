package com.iflytek.skillhub.domain.software;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Clock;
import java.time.Instant;

/**
 * One recorded inspection attempt. Every accepted attempt — success, basic
 * validation failure, metadata extraction failure, or recoverable
 * infrastructure failure — is kept for audit.
 */
@Entity
@Table(name = "software_inspection_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_software_inspection_attempt", columnNames = {"task_id", "attempt_no"})})
public class SoftwareInspectionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 10)
    private SoftwareInspectionTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SoftwareInspectionOutcome outcome;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "raw_version_fields")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String rawVersionFields;

    @Column(name = "parser_name", length = 64)
    private String parserName;

    @Column(name = "parser_version", length = 64)
    private String parserVersion;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SoftwareInspectionAttempt() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    public SoftwareInspectionAttempt(Long taskId,
                                     int attemptNo,
                                     SoftwareInspectionTrigger trigger,
                                     SoftwareInspectionOutcome outcome,
                                     String errorCode,
                                     String rawVersionFields,
                                     String parserName,
                                     String parserVersion,
                                     Instant startedAt,
                                     Instant finishedAt) {
        this.taskId = taskId;
        this.attemptNo = attemptNo;
        this.trigger = trigger;
        this.outcome = outcome;
        this.errorCode = errorCode;
        this.rawVersionFields = rawVersionFields;
        this.parserName = parserName;
        this.parserVersion = parserVersion;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public SoftwareInspectionTrigger getTrigger() {
        return trigger;
    }

    public SoftwareInspectionOutcome getOutcome() {
        return outcome;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getRawVersionFields() {
        return rawVersionFields;
    }

    public String getParserName() {
        return parserName;
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
