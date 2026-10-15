package com.iflytek.skillhub.domain.software;

import jakarta.persistence.*;
import java.time.Instant;

/** Durable capacity reservation. Failed objects remain charged until deletion succeeds. */
@Entity
@Table(name = "software_asset_upload")
public class SoftwareAssetUpload {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long releaseId;
    @Column(nullable = false, length = 128) private String ownerUserId;
    @Column(nullable = false, length = 512) private String relativePath;
    @Column(nullable = false, length = 20) private String role;
    @Column(nullable = false, length = 512) private String objectKey;
    @Column(length = 512) private String storageSessionRef;
    @Column(nullable = false) private long reservedBytes;
    @Column(nullable = false, length = 20) private String state = "RESERVED";
    @Column(nullable = false) private Instant expiresAt;
    @Version private long entityVersion;
    protected SoftwareAssetUpload() { }
    public SoftwareAssetUpload(Long releaseId, String owner, String path, String role, String objectKey,
            long bytes, Instant expiresAt) {
        this.releaseId = releaseId; this.ownerUserId = owner; this.relativePath = path;
        this.role = role; this.objectKey = objectKey; this.reservedBytes = bytes; this.expiresAt = expiresAt;
    }
    public void assignStorageSessionRef(String ref) { storageSessionRef = ref; }
    public void touch(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void complete() { state = "COMPLETED"; }
    public void requireCleanup() { state = "CLEANUP"; expiresAt = Instant.now(); }
    public void deleteUsedObject() { state = "DELETE_USED"; expiresAt = Instant.now(); }
    public void cleaned() { state = "CLEANED"; }
    public Long getId() { return id; }
    public Long getReleaseId() { return releaseId; }
    public String getOwnerUserId() { return ownerUserId; }
    public String getRelativePath() { return relativePath; }
    public String getRole() { return role; }
    public String getObjectKey() { return objectKey; }
    public String getStorageSessionRef() { return storageSessionRef; }
    public long getReservedBytes() { return reservedBytes; }
    public String getState() { return state; }
    public Instant getExpiresAt() { return expiresAt; }
}
