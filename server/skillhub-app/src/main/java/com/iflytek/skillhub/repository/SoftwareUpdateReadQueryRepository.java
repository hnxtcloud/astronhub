package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.software.*;
import com.iflytek.skillhub.service.SoftwareUpdateAppService;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Resolves published read identities and current governance together; never expose storage keys as DTOs. */
@Repository
public class SoftwareUpdateReadQueryRepository {
    private final SoftwareApplicationRepository applications;
    private final SoftwareUpdateSourceRepository sources;
    private final SoftwareUpdateChannelRepository channels;
    private final SoftwareUpdateReleaseRepository releases;
    private final SoftwareReleaseAssetRepository assets;
    private final SoftwareUpdateAppService governance;
    public SoftwareUpdateReadQueryRepository(SoftwareApplicationRepository applications, SoftwareUpdateSourceRepository sources,
            SoftwareUpdateChannelRepository channels, SoftwareUpdateReleaseRepository releases,
            SoftwareReleaseAssetRepository assets, SoftwareUpdateAppService governance) {
        this.applications = applications; this.sources = sources; this.channels = channels;
        this.releases = releases; this.assets = assets; this.governance = governance;
    }
    public List<GitLabTag> tags(String project) {
        var channel = enabledChannel(project);
        var release = governed(releases.findById(channel.getTargetReleaseId()).orElseThrow(SoftwareUpdateReadQueryRepository::missing));
        if (!channel.matches(release)) throw missing();
        return List.of(new GitLabTag(release.getTag(), new ReleaseNotes(release.getReleaseNotes()), new CommitNotes(release.getReleaseNotes())));
    }
    public AssetTarget raw(String project, String ref, String path) {
        var channel = enabledChannel(project);
        var release = releases.findByApplicationIdAndTagAndTargetOsAndTargetArchAndChannelAndVariant(channel.getApplicationId(),
                ref, channel.getTargetOs(), channel.getTargetArch(), channel.getChannel(), channel.getVariant()).orElseThrow(SoftwareUpdateReadQueryRepository::missing);
        governed(release);
        return asset(release, path);
    }
    public AssetTarget fixed(String applicationSlug, long releaseId, String path) {
        var app = applications.findBySlug(applicationSlug).orElseThrow(SoftwareUpdateReadQueryRepository::missing);
        requireSource(app.getId());
        var release = releases.findById(releaseId).filter(candidate -> candidate.getApplicationId().equals(app.getId()))
                .orElseThrow(SoftwareUpdateReadQueryRepository::missing);
        return asset(governed(release), path);
    }
    private SoftwareUpdateChannel enabledChannel(String project) {
        var channel = channels.findByVirtualProjectPath(project).orElseThrow(SoftwareUpdateReadQueryRepository::missing);
        requireSource(channel.getApplicationId());
        if (channel.getTargetReleaseId() == null) throw missing();
        var target = governed(releases.findById(channel.getTargetReleaseId()).orElseThrow(SoftwareUpdateReadQueryRepository::missing));
        if (!channel.matches(target)) throw missing();
        return channel;
    }
    private void requireSource(Long applicationId) {
        if (!sources.findById(applicationId).map(SoftwareUpdateSource::isAnonymousEnabled).orElse(false)
                || applications.findById(applicationId).filter(app -> app.getStatus() == SoftwareApplicationStatus.PUBLISHED).isEmpty()) throw missing();
    }
    private SoftwareUpdateRelease governed(SoftwareUpdateRelease release) {
        if (!"PUBLISHED".equals(release.getStatus())) throw missing();
        try { governance.requireGovernedPackages(release); }
        catch (com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException hidden) { throw missing(); }
        return release;
    }
    private AssetTarget asset(SoftwareUpdateRelease release, String path) {
        return new AssetTarget(release, assets.findByReleaseIdAndRelativePath(release.getId(), path).orElseThrow(SoftwareUpdateReadQueryRepository::missing));
    }
    public record AssetTarget(SoftwareUpdateRelease release, SoftwareReleaseAsset asset) { }
    public record GitLabTag(String name, ReleaseNotes release, CommitNotes commit) { }
    public record ReleaseNotes(String description) { }
    public record CommitNotes(String message) { }
    private static DomainNotFoundException missing() { return new DomainNotFoundException("error.software.update.notFound", "published update"); }
}
