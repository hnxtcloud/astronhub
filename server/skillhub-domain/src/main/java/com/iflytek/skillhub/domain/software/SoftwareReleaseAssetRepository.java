package com.iflytek.skillhub.domain.software;

import java.util.List;
import java.util.Optional;

/** Byte identities owned by a single update snapshot. */
public interface SoftwareReleaseAssetRepository {
    SoftwareReleaseAsset save(SoftwareReleaseAsset asset);
    Optional<SoftwareReleaseAsset> findById(Long id);
    void delete(SoftwareReleaseAsset asset);
    List<SoftwareReleaseAsset> findByReleaseIdOrderByIdAsc(Long releaseId);
    Optional<SoftwareReleaseAsset> findByReleaseIdAndRelativePath(Long releaseId, String relativePath);
}
