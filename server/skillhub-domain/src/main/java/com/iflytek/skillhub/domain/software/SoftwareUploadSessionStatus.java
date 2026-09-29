package com.iflytek.skillhub.domain.software;

/** Lifecycle of a chunked upload session. */
public enum SoftwareUploadSessionStatus {
    /** Accepting chunks. Capacity is reserved while active. */
    ACTIVE,
    /** Chunks accepted and being merged; cleanup must not touch it. */
    COMPLETING,
    /** Final object stored and the package revision created. */
    COMPLETED,
    /** Cancelled by its owner or a denied permission; temp bytes become reclaimable. */
    CANCELLED,
    /** Reclaimed by the expiry cleanup. */
    EXPIRED
}
