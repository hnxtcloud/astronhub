package com.iflytek.skillhub.domain.software;

import java.util.List;
import java.util.Optional;

/** Aggregate persistence port for update snapshots. */
public interface SoftwareUpdateReleaseRepository {
    SoftwareUpdateRelease save(SoftwareUpdateRelease release);
    Optional<SoftwareUpdateRelease> findById(Long id);
    Optional<SoftwareUpdateRelease> findLockedById(Long id);
    Optional<SoftwareUpdateRelease> findByApplicationIdAndTagAndTargetOsAndTargetArchAndChannelAndVariant(
            Long applicationId, String tag, String targetOs, String targetArch, String channel, String variant);
    Optional<SoftwareUpdateRelease> findByApplicationIdAndMachineVersionAndTargetOsAndTargetArchAndChannelAndVariant(
            Long applicationId, String machineVersion, String targetOs, String targetArch, String channel, String variant);
    List<SoftwareUpdateRelease> findByCatalogReleaseIdOrderByIdDesc(Long catalogReleaseId);
    List<SoftwareUpdateRelease> findByApplicationIdAndStatusOrderByPublishedAtDesc(Long applicationId, String status);
}
