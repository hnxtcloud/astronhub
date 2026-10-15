package com.iflytek.skillhub.domain.software;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SoftwareAssetUploadRepository {
    SoftwareAssetUpload save(SoftwareAssetUpload upload);
    Optional<SoftwareAssetUpload> findById(Long id);
    Optional<SoftwareAssetUpload> findLockedById(Long id);
    Optional<SoftwareAssetUpload> findByObjectKey(String objectKey);
    long countByOwnerUserIdAndState(String ownerUserId, String state);
    List<SoftwareAssetUpload> findByReleaseIdAndState(Long releaseId, String state);
    List<SoftwareAssetUpload> findTop20ByStateInAndExpiresAtBefore(List<String> states, Instant cutoff);
}
