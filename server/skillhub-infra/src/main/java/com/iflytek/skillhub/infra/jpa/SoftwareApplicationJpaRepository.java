package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwareApplication;
import com.iflytek.skillhub.domain.software.SoftwareApplicationRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwareApplicationJpaRepository
        extends JpaRepository<SoftwareApplication, Long>, SoftwareApplicationRepository {
}
