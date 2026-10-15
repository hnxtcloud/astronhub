package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

/** A complete update snapshot, independent of editable catalog release groupings. */
@Entity
@Table(name = "software_update_release", uniqueConstraints = @UniqueConstraint(
        name = "uk_software_update_identity", columnNames = {"application_id", "tag", "target_os", "target_arch", "channel", "variant"}))
public class SoftwareUpdateRelease {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long applicationId;
    @Column(nullable = false) private Long catalogReleaseId;
    @Column(nullable = false, length = 255) private String machineVersion;
    @Column(nullable = false, length = 255) private String tag;
    @Column(nullable = false, length = 20) private String targetOs;
    @Column(nullable = false, length = 20) private String targetArch;
    @Column(nullable = false, length = 20) private String channel;
    @Column(nullable = false, length = 64) private String variant;
    @Column(nullable = false, length = 20) private String protocol;
    @Column(length = 255) private String piVersion;
    @Column(columnDefinition = "TEXT") private String releaseNotes;
    @Column(nullable = false, length = 20) private String status = "DRAFT";
    @Column(nullable = false, length = 128) private String createdBy;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    private Instant publishedAt;
    @Column(length = 128) private String publishedBy;
    @Version private long entityVersion;

    protected SoftwareUpdateRelease() { }

    public SoftwareUpdateRelease(Long applicationId, Long catalogReleaseId, String machineVersion,
            String tag, String targetOs, String targetArch, String channel, String variant,
            String protocol, String piVersion, String releaseNotes, String createdBy) {
        SoftwareUpdateRules.identity(machineVersion, tag, targetOs, targetArch, channel, variant, protocol, piVersion);
        this.applicationId = applicationId;
        this.catalogReleaseId = catalogReleaseId;
        this.machineVersion = machineVersion;
        this.tag = tag;
        this.targetOs = targetOs;
        this.targetArch = targetArch;
        this.channel = channel;
        this.variant = variant;
        this.protocol = protocol;
        this.piVersion = piVersion;
        this.releaseNotes = releaseNotes;
        this.createdBy = createdBy;
    }

    public void requireDraft() {
        if (!"DRAFT".equals(status)) throw new DomainConflictException("error.software.update.immutable");
    }

    public void publish(List<SoftwareReleaseAsset> assets, String actor) {
        requireDraft();
        if (assets.stream().noneMatch(asset -> "INSTALLER".equals(asset.getRole()) && asset.getPackageRevisionId() != null)
                || assets.stream().noneMatch(asset -> "METADATA".equals(asset.getRole()))) {
            throw new DomainConflictException("error.software.update.incomplete");
        }
        status = "PUBLISHED";
        publishedAt = Instant.now();
        publishedBy = actor;
    }

    public void withdraw() {
        if (!"PUBLISHED".equals(status)) throw new DomainConflictException("error.software.update.notPublished");
        status = "WITHDRAWN";
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public Long getCatalogReleaseId() { return catalogReleaseId; }
    public String getMachineVersion() { return machineVersion; }
    public String getTag() { return tag; }
    public String getTargetOs() { return targetOs; }
    public String getTargetArch() { return targetArch; }
    public String getChannel() { return channel; }
    public String getVariant() { return variant; }
    public String getProtocol() { return protocol; }
    public String getPiVersion() { return piVersion; }
    public String getReleaseNotes() { return releaseNotes; }
    public String getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPublishedBy() { return publishedBy; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
