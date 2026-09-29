package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwareDownloadSession;
import com.iflytek.skillhub.domain.software.SoftwareDownloadSessionRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwareDownloadSessionJpaRepository
        extends JpaRepository<SoftwareDownloadSession, String>, SoftwareDownloadSessionRepository {

    @Override
    Optional<SoftwareDownloadSession> findById(String id);
}
