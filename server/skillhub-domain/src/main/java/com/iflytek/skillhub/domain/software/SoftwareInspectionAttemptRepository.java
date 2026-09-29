package com.iflytek.skillhub.domain.software;

import java.util.List;

/** Persistence port for recorded inspection attempts. */
public interface SoftwareInspectionAttemptRepository {

    SoftwareInspectionAttempt save(SoftwareInspectionAttempt attempt);

    List<SoftwareInspectionAttempt> findByTaskIdOrderById(Long taskId);
}
