package com.iflytek.skillhub.dto.software;

/**
 * Opens a chunked upload session. The application scope, owner and policy
 * snapshot are bound server-side; chunk requests can never change them.
 */
public record SoftwareUploadSessionCreateRequest(
        Long releaseId,
        String fileName,
        String format,
        String targetOs,
        String targetArch,
        String targetDistro,
        String applicabilityNote,
        String originalFullVersion,
        long byteSize
) {
}
