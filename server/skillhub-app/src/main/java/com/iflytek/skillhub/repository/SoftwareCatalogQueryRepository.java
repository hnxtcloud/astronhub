package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.software.SoftwareApplicationStatus;
import com.iflytek.skillhub.domain.software.SoftwareTargetArch;
import com.iflytek.skillhub.domain.software.SoftwareTargetOs;
import com.iflytek.skillhub.dto.software.SoftwareAppAdminSummaryResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppDetailResponse;
import com.iflytek.skillhub.dto.software.SoftwareAppSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read model for the portal software catalog. Assembles applications,
 * releases, packages, recommendations, and category names across the software
 * aggregates; presentation projection stays out of app services.
 */
public interface SoftwareCatalogQueryRepository {

    Page<SoftwareAppSummaryResponse> searchPublishedApps(String keyword,
                                                         String categoryCode,
                                                         SoftwareTargetOs targetOs,
                                                         SoftwareTargetArch targetArch,
                                                         Pageable pageable);

    SoftwareAppDetailResponse findPublishedAppDetail(String slug);

    /** Admin variant: includes draft and taken-down applications. */
    Page<SoftwareAppAdminSummaryResponse> searchAppsForAdmin(String keyword,
                                                             String categoryCode,
                                                             SoftwareApplicationStatus status,
                                                             Pageable pageable);
}
