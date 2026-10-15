package com.iflytek.skillhub.domain.software;
import java.util.List;
import java.util.Optional;

public interface SoftwareUpdateChannelRepository {
    SoftwareUpdateChannel save(SoftwareUpdateChannel channel);
    List<SoftwareUpdateChannel> findByApplicationIdOrderByIdAsc(Long applicationId);
    Optional<SoftwareUpdateChannel> findByApplicationIdAndSlug(Long applicationId, String slug);
    Optional<SoftwareUpdateChannel> findByVirtualProjectPath(String virtualProjectPath);
}
