package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.software.SoftwareReleaseAsset;
import com.iflytek.skillhub.domain.software.SoftwareReleaseAssetRepository;
import com.iflytek.skillhub.domain.software.SoftwareUpdateRelease;
import com.iflytek.skillhub.dto.software.SoftwareReleaseAssetResponse;
import com.iflytek.skillhub.dto.software.SoftwareUpdateReleaseResponse;
import org.springframework.stereotype.Repository;

/** Joins immutable snapshot identities and assets for the management read model. */
@Repository
public class SoftwareUpdateQueryRepository {
    private final SoftwareReleaseAssetRepository assets;
    public SoftwareUpdateQueryRepository(SoftwareReleaseAssetRepository assets) { this.assets = assets; }
    public SoftwareUpdateReleaseResponse project(SoftwareUpdateRelease release) {
        return new SoftwareUpdateReleaseResponse(release.getId(), release.getApplicationId(), release.getCatalogReleaseId(),
                release.getMachineVersion(), release.getTag(), release.getTargetOs(), release.getTargetArch(),
                release.getChannel(), release.getVariant(), release.getProtocol(), release.getPiVersion(), release.getStatus(),
                release.getReleaseNotes(), release.getPublishedAt(), assets.findByReleaseIdOrderByIdAsc(release.getId()).stream().map(this::asset).toList());
    }
    public SoftwareReleaseAssetResponse asset(SoftwareReleaseAsset asset) {
        return new SoftwareReleaseAssetResponse(asset.getId(), asset.getRelativePath(), asset.getRole(),
                asset.getPackageRevisionId(), asset.getByteSize(), asset.getSha256(), asset.getSha512Base64());
    }
    public com.iflytek.skillhub.dto.software.SoftwareUpdateChannelResponse channel(com.iflytek.skillhub.domain.software.SoftwareUpdateChannel channel) {
        return new com.iflytek.skillhub.dto.software.SoftwareUpdateChannelResponse(channel.getId(), channel.getSlug(),
                channel.getVirtualProjectPath(), channel.getTargetOs(), channel.getTargetArch(), channel.getChannel(),
                channel.getVariant(), channel.getTargetReleaseId());
    }
}
