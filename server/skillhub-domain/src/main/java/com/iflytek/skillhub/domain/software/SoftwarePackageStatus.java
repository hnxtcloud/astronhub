package com.iflytek.skillhub.domain.software;

/**
 * Lifecycle state of an installable package revision. A package is
 * downloadable only while both the application and the package are
 * {@link #PUBLISHED} and the stored file is available.
 */
public enum SoftwarePackageStatus {
    DRAFT,
    PUBLISHED,
    TAKEN_DOWN
}
