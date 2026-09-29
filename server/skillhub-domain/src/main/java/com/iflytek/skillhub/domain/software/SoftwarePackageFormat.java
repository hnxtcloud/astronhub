package com.iflytek.skillhub.domain.software;

/**
 * Installable package formats supported by the software center first version.
 * MSI/EXE/DEB/RPM receive automatic static inspection; archive formats are
 * filled in manually by administrators.
 */
public enum SoftwarePackageFormat {
    MSI,
    EXE,
    DEB,
    RPM,
    ZIP,
    TAR_GZ,
    TAR_XZ;

    /** True when the format belongs to the automatic static-inspection set. */
    public boolean isAutoInspected() {
        return this == MSI || this == EXE || this == DEB || this == RPM;
    }
}
