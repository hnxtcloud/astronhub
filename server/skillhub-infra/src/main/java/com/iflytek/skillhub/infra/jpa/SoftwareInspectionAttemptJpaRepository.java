package com.iflytek.skillhub.infra.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA implementation of the inspection attempt port.
 */
public interface SoftwareInspectionAttemptJpaRepository
        extends JpaRepository<com.iflytek.skillhub.domain.software.SoftwareInspectionAttempt, Long>,
        com.iflytek.skillhub.domain.software.SoftwareInspectionAttemptRepository {

    @Override
    List<com.iflytek.skillhub.domain.software.SoftwareInspectionAttempt> findByTaskIdOrderById(Long taskId);
}
