package com.iflytek.skillhub.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.publication.PublicationTask;
import com.iflytek.skillhub.dto.publication.PublicationDraft;
import com.iflytek.skillhub.dto.publication.PublicationProgressView;
import com.iflytek.skillhub.dto.publication.PublicationProgressView.PublicationResultView;
import com.iflytek.skillhub.dto.publication.PublicationTaskView;
import com.iflytek.skillhub.dto.plugin.PluginImportDtos.ImportPreviewView;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Projects persisted checkpoints and current domain states for publication screens. */
@Repository
public class PublicationTaskQueryRepository {
    private final ObjectMapper mapper;
    private final PublicationResultQueryRepository resultsQuery;
    public PublicationTaskQueryRepository(ObjectMapper mapper, PublicationResultQueryRepository resultsQuery) {
        this.mapper = mapper;
        this.resultsQuery = resultsQuery;
    }
    public PublicationTaskView project(PublicationTask task) {
        try {
            var progress = mapper.readTree(task.getProgressJson());
            var results = new ArrayList<PublicationResultView>();
            if (progress.path("results").isArray()) for (var row : progress.path("results")) results.add(resultsQuery.current(
                    mapper.treeToValue(row, PublicationResultView.class)));
            var warnings = new ArrayList<String>();
            if (progress.path("warnings").isArray()) progress.path("warnings").forEach(w -> warnings.add(w.asText()));
            var preview = progress.hasNonNull("preview") ? mapper.treeToValue(progress.get("preview"), ImportPreviewView.class) : null;
            var upload = progress.path("materialUpload");
            var uploadView = upload.isObject() ? new PublicationProgressView.PublicationUploadView(upload.path("totalBytes").asLong(),
                    upload.path("uploadedBytes").asLong(), upload.path("chunkSizeBytes").asInt(), upload.path("chunkDigests").size(),
                    upload.path("totalChunks").asInt(), mapper.convertValue(upload.path("chunkDigests"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {})) : null;
            return new PublicationTaskView(task.getId(), task.getStatus(), task.getRevision(), mapper.readValue(task.getDraftJson(), PublicationDraft.class),
                    new PublicationProgressView(progress.path("phase").asText("MATERIAL"), progress.path("error").asText(null), results, preview, warnings,
                            progress.path("detectedType").asText("UNKNOWN"), uploadView),
                    task.getFileName(), task.getByteSize(), task.getMaterialKey() != null,
                    task.getCreatedAt(), task.getUpdatedAt());
        } catch (JsonProcessingException e) { throw new IllegalStateException("Invalid publication task", e); }
    }
}
