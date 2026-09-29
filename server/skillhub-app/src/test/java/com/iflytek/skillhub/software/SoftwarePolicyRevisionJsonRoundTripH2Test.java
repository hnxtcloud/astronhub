package com.iflytek.skillhub.software;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.software.SoftwarePolicyConfig;
import com.iflytek.skillhub.domain.software.SoftwarePolicyConfigJson;
import com.iflytek.skillhub.domain.software.SoftwarePolicyRevision;
import com.iflytek.skillhub.domain.software.SoftwarePolicyRevisionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Guards the settings read path against the H2 JSON storage quirk: H2 never
 * parses character input into a JSON document on write, so a revision saved
 * through JPA re-reads as a once-wrapped JSON string (PostgreSQL stores the
 * object directly). {@link SoftwarePolicyConfigJson} must tolerate both
 * forms; otherwise every settings GET after an update or rollback reports a
 * corrupted saved policy — exactly what the E2E flow exposed.
 */
@SpringBootTest
@ActiveProfiles("test")
class SoftwarePolicyRevisionJsonRoundTripH2Test {

    @Autowired
    private SoftwarePolicyRevisionRepository revisionRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void jpaWrittenConfigParsesAfterReload() {
        transactionTemplate.executeWithoutResult(status -> {
            revisionRepository.save(new SoftwarePolicyRevision(
                    990_001, SoftwarePolicyConfig.SCHEMA_VERSION,
                    SoftwarePolicyConfigJson.toJson(validConfig()), "roundtrip-test", "json round-trip guard"));
            entityManager.flush();
            entityManager.clear();
        });

        SoftwarePolicyRevision reloaded = revisionRepository.findByRevision(990_001L)
                .orElseThrow();

        SoftwarePolicyConfig config = SoftwarePolicyConfigJson.fromJson(reloaded.getConfig());
        assertThat(config.maxConcurrentInspectionsPerWorker()).isEqualTo(1);
        assertThat(config.maxPackageBytes()).isEqualTo(5368709120L);
    }

    private SoftwarePolicyConfig validConfig() {
        return new SoftwarePolicyConfig(
                5368709120L, 536870912000L, 2, 8388608L, 1440L, 168L, 5L,
                1, 900L, 60L, 536870912L, 21474836480L, 10000, 3, 1440L);
    }
}
