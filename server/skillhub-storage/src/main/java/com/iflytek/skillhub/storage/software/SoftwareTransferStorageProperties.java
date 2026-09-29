package com.iflytek.skillhub.storage.software;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Deployment-level locations for software-center transfer bytes. */
@Component
@ConfigurationProperties(prefix = "skillhub.software.storage")
public class SoftwareTransferStorageProperties {

    /** Controlled directory for in-flight chunk bytes and merge scratch files. */
    private String tempDir = "./data/software-upload-tmp";

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }
}
