package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.software.SoftwareApplicationRepository;
import com.iflytek.skillhub.domain.software.SoftwareReleaseAsset;
import com.iflytek.skillhub.domain.software.SoftwareReleaseAssetRepository;
import com.iflytek.skillhub.domain.software.SoftwareUpdateChannelRepository;
import com.iflytek.skillhub.domain.software.SoftwareUpdateRelease;
import com.iflytek.skillhub.domain.software.SoftwareUpdateSource;
import com.iflytek.skillhub.domain.software.SoftwareUpdateSourceRepository;
import com.iflytek.skillhub.dto.software.SoftwareReleaseAssetResponse;
import com.iflytek.skillhub.dto.software.SoftwareUpdateReleaseResponse;
import com.iflytek.skillhub.dto.software.SoftwareUpdateSourceResponse;
import org.springframework.stereotype.Repository;

/** Joins immutable snapshot identities and assets for the management read model. */
@Repository
public class SoftwareUpdateQueryRepository {
    private final SoftwareReleaseAssetRepository assets;
    private final SoftwareApplicationRepository applications;
    private final SoftwareUpdateSourceRepository sources;
    private final SoftwareUpdateChannelRepository channels;
    public SoftwareUpdateQueryRepository(SoftwareReleaseAssetRepository assets, SoftwareApplicationRepository applications,
            SoftwareUpdateSourceRepository sources, SoftwareUpdateChannelRepository channels) {
        this.assets = assets; this.applications = applications; this.sources = sources; this.channels = channels;
    }
    public SoftwareUpdateSourceResponse source(Long applicationId) {
        applications.findById(applicationId).orElseThrow(() -> new DomainNotFoundException("error.software.update.notFound", "application"));
        boolean enabled = sources.findById(applicationId).map(SoftwareUpdateSource::isAnonymousEnabled).orElse(false);
        return new SoftwareUpdateSourceResponse(applicationId, enabled,
                channels.findByApplicationIdOrderByIdAsc(applicationId).stream().map(this::channel).toList());
    }
    public SoftwareUpdateReleaseResponse project(SoftwareUpdateRelease release) {
        return new SoftwareUpdateReleaseResponse(release.getId(), release.getApplicationId(), release.getCatalogReleaseId(),
                release.getMachineVersion(), release.getTag(), release.getTargetOs(), release.getTargetArch(),
                release.getChannel(), release.getVariant(), release.getProtocol(), release.getPiVersion(), release.getStatus(),
                release.getReleaseNotes(), release.getPublishedAt(), assets.findByReleaseIdOrderByIdAsc(release.getId()).stream().map(this::asset).toList());
    }
    public SoftwareReleaseAssetResponse asset(SoftwareReleaseAsset asset) {
        return new SoftwareReleaseAssetResponse(asset.getId(), asset.getRelativePath(), asset.getRole(),
                asset.getPackageRevisionId(), asset.getByteSize(), asset.getSha256(), asset.getSha512Base64(),
                asset.getConfirmedInstallerFamily(), asset.getInstallerFamilyConfirmedBy(), asset.getInstallerFamilyConfirmedAt());
    }
    public com.iflytek.skillhub.dto.software.SoftwareUpdateChannelResponse channel(com.iflytek.skillhub.domain.software.SoftwareUpdateChannel channel) {
        return new com.iflytek.skillhub.dto.software.SoftwareUpdateChannelResponse(channel.getId(), channel.getSlug(),
                channel.getVirtualProjectPath(), channel.getTargetOs(), channel.getTargetArch(), channel.getChannel(),
                channel.getVariant(), channel.getTargetReleaseId());
    }
}
