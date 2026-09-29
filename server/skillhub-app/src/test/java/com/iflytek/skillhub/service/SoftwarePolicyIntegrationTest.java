package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.software.SoftwarePolicyConfig;
import com.iflytek.skillhub.domain.software.SoftwarePolicyDeploymentLimits;
import com.iflytek.skillhub.domain.software.SoftwarePolicyRevision;
import com.iflytek.skillhub.domain.software.SoftwarePolicyRevisionRepository;
import com.iflytek.skillhub.domain.software.SoftwarePolicyService;
import com.iflytek.skillhub.domain.software.SoftwarePolicySnapshot;
import com.iflytek.skillhub.domain.software.SoftwarePolicyState;
import com.iflytek.skillhub.domain.software.SoftwarePolicyStateRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the versioned software policy against a real database: revision
 * persistence, JSON round-trips, optimistic conflict detection, atomic pointer
 * switching, and rollback — the admission path relies on all of these.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SoftwarePolicyIntegrationTest {

    @Autowired
    private SoftwarePolicyService policyService;

    @Autowired
    private SoftwarePolicyRevisionRepository revisionRepository;

    @Autowired
    private SoftwarePolicyStateRepository stateRepository;

    @Autowired
    private SoftwarePolicyDeploymentLimits deploymentLimits;

    @BeforeEach
    void seedInitialRevision() {
        if (policyService.findActive() != null) {
            return;
        }
        SoftwarePolicyRevision initial = revisionRepository.save(new SoftwarePolicyRevision(
                1, SoftwarePolicyConfig.SCHEMA_VERSION,
                com.iflytek.skillhub.domain.software.SoftwarePolicyConfigJson.toJson(
                        SoftwarePolicyConfig.defaults()),
                "system", "test seed"));
        stateRepository.save(new SoftwarePolicyState(initial.getId()));
    }

    @Test
    void activeSnapshot_roundTripsTypedConfiguration() {
        SoftwarePolicySnapshot snapshot = policyService.requireActive();

        assertThat(snapshot.revision()).isEqualTo(1L);
        assertThat(snapshot.config()).isEqualTo(SoftwarePolicyConfig.defaults());
    }

    @Test
    void update_persistsNewRevisionAndMovesPointer() {
        SoftwarePolicyConfig changed = new SoftwarePolicyConfig(4L * 1024 * 1024 * 1024,
                400L * 1024 * 1024 * 1024, 2, 8L * 1024 * 1024, 1440, 168, 5, 1, 900, 60,
                512L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);

        SoftwarePolicySnapshot after = policyService.update(1L, changed, "admin-1", "调低配额");

        assertThat(after.revision()).isEqualTo(2L);
        SoftwarePolicySnapshot reread = policyService.requireActive();
        assertThat(reread.revision()).isEqualTo(2L);
        assertThat(reread.config().maxPackageBytes()).isEqualTo(4L * 1024 * 1024 * 1024);
        assertThat(reread.config().totalQuotaBytes()).isEqualTo(400L * 1024 * 1024 * 1024);
        // History keeps every revision, newest last write included.
        List<SoftwarePolicyRevision> revisions = policyService.history(
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertThat(revisions).extracting(SoftwarePolicyRevision::getRevision)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void update_rejectsStaleRevisionWithConflict() {
        policyService.update(1L, SoftwarePolicyConfig.defaults(), "admin-1", "change one");

        assertThrows(DomainConflictException.class,
                () -> policyService.update(1L, SoftwarePolicyConfig.defaults(), "admin-2", "change two"));
    }

    @Test
    void rollback_reactivatesHistoricalValuesAsNewRevision() {
        SoftwarePolicyConfig lowered = new SoftwarePolicyConfig(1L * 1024 * 1024 * 1024,
                500L * 1024 * 1024 * 1024, 2, 8L * 1024 * 1024, 1440, 168, 5, 1, 900, 60,
                512L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);
        policyService.update(1L, lowered, "admin-1", "调低单包");

        SoftwarePolicySnapshot rolledBack = policyService.rollback(1L, "admin-1", "恢复默认");

        assertThat(rolledBack.revision()).isEqualTo(3L);
        assertThat(rolledBack.config()).isEqualTo(SoftwarePolicyConfig.defaults());
        assertThat(policyService.requireActive().config()).isEqualTo(SoftwarePolicyConfig.defaults());
    }

    @Test
    void update_rejectsValuesBeyondDeploymentLimits() {
        SoftwarePolicyConfig oversizedChunk = new SoftwarePolicyConfig(5L * 1024 * 1024 * 1024,
                500L * 1024 * 1024 * 1024, 2, 128L * 1024 * 1024, 1440, 168, 5, 1, 900, 60,
                512L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);

        assertThrows(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class,
                () -> policyService.update(1L, oversizedChunk, "admin-1", "超过代理上限"));
        assertThat(deploymentLimits.proxyMaxBodyBytes()).isLessThan(oversizedChunk.chunkSizeBytes());
    }
}
