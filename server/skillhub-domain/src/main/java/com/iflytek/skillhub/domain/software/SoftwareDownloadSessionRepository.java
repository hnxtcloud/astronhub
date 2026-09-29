package com.iflytek.skillhub.domain.software;

import java.util.Optional;

public interface SoftwareDownloadSessionRepository {

    SoftwareDownloadSession save(SoftwareDownloadSession session);

    Optional<SoftwareDownloadSession> findById(String id);
}
