package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.software.SoftwareInspectionTaskRepository;
import com.iflytek.skillhub.domain.software.SoftwarePackageRevision;
import com.iflytek.skillhub.dto.software.SoftwareMachinePackageResponse;
import com.iflytek.skillhub.dto.software.SoftwarePackageResponse;
import com.iflytek.skillhub.service.SoftwareInspectionAppService;

import org.springframework.stereotype.Repository;

/** Joins authorized package facts with the existing inspection detail read model. */
@Repository
public class SoftwareMachinePublishingQueryRepository {
    private final SoftwareInspectionTaskRepository tasks;
    private final SoftwareInspectionAppService inspections;

    public SoftwareMachinePublishingQueryRepository(
            SoftwareInspectionTaskRepository tasks, SoftwareInspectionAppService inspections) {
        this.tasks = tasks;
        this.inspections = inspections;
    }

    public SoftwareMachinePackageResponse packageStatus(SoftwarePackageRevision pkg) {
        var inspection =
                tasks.findByPackageRevisionId(pkg.getId())
                        .map(task -> inspections.detail(task.getId()))
                        .orElse(null);
        return new SoftwareMachinePackageResponse(project(pkg), inspection);
    }

    public SoftwarePackageResponse project(SoftwarePackageRevision pkg) {
        return new SoftwarePackageResponse(
                pkg.getId(),
                pkg.getRevisionNo(),
                pkg.getStatus().name(),
                pkg.getTargetOs().name(),
                pkg.getTargetArch().name(),
                pkg.getTargetDistro(),
                pkg.getFormat().name(),
                pkg.getOriginalFullVersion(),
                pkg.getFileName(),
                pkg.getByteSize(),
                pkg.getSha256(),
                pkg.getApplicabilityNote(),
                pkg.getConfirmedBy(),
                pkg.getConfirmedAt(),
                pkg.getPublishedAt());
    }
}
