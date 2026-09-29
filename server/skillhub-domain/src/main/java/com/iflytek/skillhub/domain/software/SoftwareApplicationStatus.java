package com.iflytek.skillhub.domain.software;

/**
 * Lifecycle state of a software application. Releases are groupings without
 * their own status; package visibility decides whether a release is usable.
 */
public enum SoftwareApplicationStatus {
    DRAFT,
    PUBLISHED,
    TAKEN_DOWN
}
