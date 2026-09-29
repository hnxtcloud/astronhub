package com.iflytek.skillhub.domain.software;

/**
 * Outcome of one inspection attempt as reported by the worker. Stale reports
 * (expired lease, wrong attempt token) are rejected before an outcome is ever
 * recorded.
 */
public enum SoftwareInspectionOutcome {
    SUCCEEDED,
    BASIC_FAILED,
    METADATA_FAILED,
    INFRA_FAILED
}
