package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.software.SoftwareInspectionTaskStatus;
import com.iflytek.skillhub.dto.software.SoftwareInspectionSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read model for the admin inspection console: inspection tasks joined with
 * their package revision facts (file name, declared version, size).
 */
public interface SoftwareInspectionQueryRepository {

    Page<SoftwareInspectionSummaryResponse> search(SoftwareInspectionTaskStatus status, Pageable pageable);

    SoftwareInspectionSummaryResponse findByTaskId(Long taskId);
}
