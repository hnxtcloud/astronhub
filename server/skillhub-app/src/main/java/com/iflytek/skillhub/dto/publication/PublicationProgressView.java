package com.iflytek.skillhub.dto.publication;

import com.iflytek.skillhub.dto.plugin.PluginImportDtos.ImportPreviewView;
import java.util.List;

public record PublicationProgressView(String phase, String error, List<PublicationResultView> results,
        ImportPreviewView preview, List<String> warnings, String detectedType, PublicationUploadView materialUpload) {
    public record PublicationUploadView(long totalBytes, long uploadedBytes, int chunkSizeBytes,
            int receivedChunks, int totalChunks, List<String> chunkDigests) {}
    public record PublicationResultView(String candidateId, String type, String status,
            String namespace, String slug, String version, Long resourceId, Long releaseId, String error, Long packageRevisionId) {}
}
