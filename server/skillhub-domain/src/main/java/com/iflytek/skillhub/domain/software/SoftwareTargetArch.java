package com.iflytek.skillhub.domain.software;

/**
 * Confirmed CPU architecture tag for a package. {@code UNKNOWN} means the
 * architecture is not determined; {@code NOARCH} means the package itself
 * declares it is CPU-architecture independent (it does not imply
 * cross-operating-system portability).
 */
public enum SoftwareTargetArch {
    X86,
    X86_64,
    ARM64,
    NOARCH,
    UNKNOWN
}
