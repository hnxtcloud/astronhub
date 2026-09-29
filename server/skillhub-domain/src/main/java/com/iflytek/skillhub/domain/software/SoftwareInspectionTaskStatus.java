package com.iflytek.skillhub.domain.software;

/**
 * Lifecycle of a persistent static-inspection task. BASIC_FAILED is a real
 * validation failure that blocks publication; METADATA_FAILED means only the
 * extraction of version metadata failed and administrators can fill values in
 * manually; EXHAUSTED means the auto-retry budget for recoverable
 * infrastructure failures ran out.
 */
public enum SoftwareInspectionTaskStatus {
    PENDING,
    LEASED,
    SUCCEEDED,
    BASIC_FAILED,
    METADATA_FAILED,
    EXHAUSTED;

    /**
     * True when an administrator may trigger a controlled manual retry:
     * failed outcomes, plus PENDING for tasks stuck without a worker.
     */
    public boolean isManuallyRetriable() {
        return this == PENDING || this == BASIC_FAILED || this == METADATA_FAILED || this == EXHAUSTED;
    }

    /** True when the task has reached a terminal outcome. */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == BASIC_FAILED
                || this == METADATA_FAILED || this == EXHAUSTED;
    }
}
