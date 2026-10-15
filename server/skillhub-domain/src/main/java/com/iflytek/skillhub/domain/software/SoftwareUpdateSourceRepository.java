package com.iflytek.skillhub.domain.software;
import java.util.Optional;

public interface SoftwareUpdateSourceRepository {
    SoftwareUpdateSource save(SoftwareUpdateSource source);
    Optional<SoftwareUpdateSource> findById(Long applicationId);
}
