package com.iflytek.skillhub.domain.software;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable stored-byte identity; installers reference existing package storage. */
@Entity
@Table(name = "software_release_asset", uniqueConstraints = @UniqueConstraint(columnNames = {"release_id", "relative_path"}))
public class SoftwareReleaseAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "release_id", nullable = false) private Long releaseId;
    @Column(name = "relative_path", nullable = false, length = 512) private String relativePath;
    @Column(nullable = false, length = 20) private String role;
    private Long packageRevisionId;
    @Column(nullable = false, length = 512) private String objectKey;
    @Column(nullable = false) private long byteSize;
    @Column(nullable = false, length = 64) private String sha256;
    // Explicit name required: the default camel-case strategy maps `sha512Base64`
    // to `sha512base64` (no underscore before a digit), while V75 created `sha512_base64`.
    @Column(name = "sha512_base64", nullable = false, length = 88) private String sha512Base64;
    @Column(length = 20, updatable = false) private String confirmedInstallerFamily;
    @Column(length = 128, updatable = false) private String installerFamilyConfirmedBy;
    @Column(updatable = false) private Instant installerFamilyConfirmedAt;
    protected SoftwareReleaseAsset() { }
    public SoftwareReleaseAsset(Long releaseId, String relativePath, String role, Long packageRevisionId,
            String objectKey, long byteSize, String sha256, String sha512Base64) {
        this(releaseId, relativePath, role, packageRevisionId, objectKey, byteSize, sha256, sha512Base64, null, null, null);
    }
    public SoftwareReleaseAsset(Long releaseId, String relativePath, String role, Long packageRevisionId,
            String objectKey, long byteSize, String sha256, String sha512Base64, String confirmedInstallerFamily,
            String installerFamilyConfirmedBy, Instant installerFamilyConfirmedAt) {
        SoftwareUpdateRules.path(relativePath);
        if (confirmedInstallerFamily != null && (!"NSIS".equals(confirmedInstallerFamily)
                || !"INSTALLER".equals(role) || packageRevisionId == null
                || installerFamilyConfirmedBy == null || installerFamilyConfirmedBy.isBlank()
                || installerFamilyConfirmedAt == null)) SoftwareUpdateRules.invalid("installer family confirmation");
        if (confirmedInstallerFamily == null && (installerFamilyConfirmedBy != null || installerFamilyConfirmedAt != null))
            SoftwareUpdateRules.invalid("installer family confirmation");
        this.releaseId = releaseId;
        this.relativePath = relativePath;
        this.role = role;
        this.packageRevisionId = packageRevisionId;
        this.objectKey = objectKey;
        this.byteSize = byteSize;
        this.sha256 = sha256;
        this.sha512Base64 = sha512Base64;
        this.confirmedInstallerFamily = confirmedInstallerFamily;
        this.installerFamilyConfirmedBy = installerFamilyConfirmedBy;
        this.installerFamilyConfirmedAt = installerFamilyConfirmedAt;
    }
    public Long getId() { return id; }
    public Long getReleaseId() { return releaseId; }
    public String getRelativePath() { return relativePath; }
    public String getRole() { return role; }
    public Long getPackageRevisionId() { return packageRevisionId; }
    public String getObjectKey() { return objectKey; }
    public long getByteSize() { return byteSize; }
    public String getSha256() { return sha256; }
    public String getSha512Base64() { return sha512Base64; }
    public String getConfirmedInstallerFamily() { return confirmedInstallerFamily; }
    public String getInstallerFamilyConfirmedBy() { return installerFamilyConfirmedBy; }
    public Instant getInstallerFamilyConfirmedAt() { return installerFamilyConfirmedAt; }
    public boolean hasConfirmedNsisInstaller() {
        return "NSIS".equals(confirmedInstallerFamily) && installerFamilyConfirmedBy != null
                && !installerFamilyConfirmedBy.isBlank() && installerFamilyConfirmedAt != null;
    }
}
