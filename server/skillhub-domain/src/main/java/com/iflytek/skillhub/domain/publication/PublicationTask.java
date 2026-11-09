package com.iflytek.skillhub.domain.publication;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A publisher-owned workflow record, separate from skill/plugin/software lifecycle state. */
@Entity
@Table(name = "publication_task")
public class PublicationTask {
    @Id @Column(length = 36) private String id;
    @Column(name = "owner_id", nullable = false, length = 128) private String ownerId;
    @Column(nullable = false, length = 24) private String status;
    @Column(nullable = false) private long revision;
    @Version private long entityVersion;
    @Column(name = "draft_json", nullable = false, columnDefinition = "TEXT") private String draftJson;
    @Column(name = "progress_json", nullable = false, columnDefinition = "TEXT") private String progressJson;
    @Column(name = "material_key", length = 512) private String materialKey;
    @Column(name = "file_name", length = 255) private String fileName;
    @Column(name = "byte_size") private long byteSize;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PublicationTask() {}
    public PublicationTask(String ownerId, String draftJson) {
        id = UUID.randomUUID().toString();
        this.ownerId = ownerId;
        this.draftJson = draftJson;
        progressJson = "{}";
        status = "DRAFT";
        createdAt = updatedAt = Instant.now();
    }
    public String getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getStatus() { return status; }
    public long getRevision() { return revision; }
    public String getDraftJson() { return draftJson; }
    public String getProgressJson() { return progressJson; }
    public String getMaterialKey() { return materialKey; }
    public String getFileName() { return fileName; }
    public long getByteSize() { return byteSize; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void updateDraft(String json) { draftJson = json; touch(); }
    public void updateProgress(String json, String status) {
        progressJson = json; this.status = status; touch();
    }
    public void attach(String key, String name, long size) {
        materialKey = key; fileName = name; byteSize = size; touch();
    }
    private void touch() { revision++; updatedAt = Instant.now(); }
}
