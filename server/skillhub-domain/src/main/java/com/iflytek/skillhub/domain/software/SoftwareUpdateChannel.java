package com.iflytek.skillhub.domain.software;
import jakarta.persistence.*;
import java.util.Objects;

/** A virtual project maps exactly one application/platform/channel/variant to an explicit current snapshot. */
@Entity
@Table(name = "software_update_channel", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"application_id", "slug"}),
        @UniqueConstraint(columnNames = "virtual_project_path")})
public class SoftwareUpdateChannel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "application_id", nullable = false) private Long applicationId;
    @Column(nullable = false, length = 64) private String slug;
    @Column(name = "virtual_project_path", nullable = false, length = 512) private String virtualProjectPath;
    @Column(nullable = false, length = 20) private String targetOs;
    @Column(nullable = false, length = 20) private String targetArch;
    @Column(nullable = false, length = 20) private String channel;
    @Column(nullable = false, length = 64) private String variant;
    private Long targetReleaseId;
    @Version private long entityVersion;
    protected SoftwareUpdateChannel() { }
    public SoftwareUpdateChannel(Long applicationId, String slug) { this.applicationId = applicationId; this.slug = slug; }
    public void configure(String project, String os, String arch, String channel, String variant, Long target) {
        if (slug == null || !slug.matches("[a-z0-9][a-z0-9-]{0,63}")) SoftwareUpdateRules.invalid("channel slug");
        SoftwareUpdateRules.path(project);
        if (!project.matches("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)+")) SoftwareUpdateRules.invalid("virtual project path");
        SoftwareUpdateRules.identity("0.0.0", "0.0.0", os, arch, channel, variant, "ELECTRON", null);
        this.virtualProjectPath = project; this.targetOs = os; this.targetArch = arch;
        this.channel = channel; this.variant = variant; this.targetReleaseId = target;
    }
    public boolean matches(SoftwareUpdateRelease release) {
        return Objects.equals(applicationId, release.getApplicationId()) && targetOs.equals(release.getTargetOs())
                && targetArch.equals(release.getTargetArch()) && channel.equals(release.getChannel()) && variant.equals(release.getVariant());
    }
    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public String getSlug() { return slug; }
    public String getVirtualProjectPath() { return virtualProjectPath; }
    public String getTargetOs() { return targetOs; }
    public String getTargetArch() { return targetArch; }
    public String getChannel() { return channel; }
    public String getVariant() { return variant; }
    public Long getTargetReleaseId() { return targetReleaseId; }
}
