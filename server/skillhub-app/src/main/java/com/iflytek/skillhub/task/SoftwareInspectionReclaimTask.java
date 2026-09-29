package com.iflytek.skillhub.task;

import com.iflytek.skillhub.domain.software.SoftwareInspectionTaskService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic reclaim pass for inspection leases that expired without a result.
 * Tasks with auto-retry budget left simply become claimable again through the
 * claim query; this pass closes the ones whose budget is spent as EXHAUSTED so
 * administrators see them instead of the tasks lingering in LEASED forever.
 */
@Component
public class SoftwareInspectionReclaimTask {

    private static final Logger log = LoggerFactory.getLogger(SoftwareInspectionReclaimTask.class);

    private final SoftwareInspectionTaskService inspectionTaskService;

    public SoftwareInspectionReclaimTask(SoftwareInspectionTaskService inspectionTaskService) {
        this.inspectionTaskService = inspectionTaskService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void reclaimExpiredLeases() {
        int exhausted = inspectionTaskService.reclaimExpiredLeases(Instant.now(), 20);
        if (exhausted > 0) {
            log.warn("Closed {} inspection task(s) as EXHAUSTED after expired leases", exhausted);
        }
    }
}
