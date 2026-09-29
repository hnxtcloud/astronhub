package com.iflytek.skillhub.domain.software;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Clock;
import java.time.Instant;

/**
 * One accepted chunk of an upload session. The DB manifest is the source of
 * truth for resume validation; storage-side records are never trusted alone.
 * Re-accepting a chunk with the same digest is idempotent; a conflicting
 * digest is a business conflict (409) — bytes are never silently replaced.
 */
@Entity
@Table(name = "software_upload_chunk", uniqueConstraints = {
        @UniqueConstraint(name = "uk_software_upload_chunk",
                columnNames = {"session_id", "chunk_no"})
})
public class SoftwareUploadChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "chunk_no", nullable = false)
    private int chunkNo;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SoftwareUploadChunk() {
    }

    public SoftwareUploadChunk(Long sessionId, int chunkNo, long byteSize, String sha256) {
        this.sessionId = sessionId;
        this.chunkNo = chunkNo;
        this.byteSize = byteSize;
        this.sha256 = sha256;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public int getChunkNo() {
        return chunkNo;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
