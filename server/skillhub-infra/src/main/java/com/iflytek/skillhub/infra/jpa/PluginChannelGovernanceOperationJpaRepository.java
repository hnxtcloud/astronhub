package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.*;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

import java.time.Instant;
import java.util.*;

/**
 * Use the same native FOR UPDATE as the channel ledger: it retains a real pessimistic write lock
 * on both PostgreSQL and the H2 fixture, without dialect-specific FOR NO KEY UPDATE generation.
 */
public interface PluginChannelGovernanceOperationJpaRepository
        extends JpaRepository<PluginChannelGovernanceOperation, Long>,
                PluginChannelGovernanceOperationRepository {
    @Query(
            value = "SELECT * FROM plugin_channel_governance_operation WHERE id=:id FOR UPDATE",
            nativeQuery = true)
    Optional<PluginChannelGovernanceOperation> findByIdForUpdate(Long id);

    @Query(
            "select o from PluginChannelGovernanceOperation o where (o.state in"
                + " ('QUEUED','FAILED','UNKNOWN') or (o.state='RUNNING' and o.leaseUntil<=:now))"
                + " and (o.nextAttemptAt is null or o.nextAttemptAt<=:now) order by"
                + " o.attemptCount,o.createdAt,o.id")
    List<PluginChannelGovernanceOperation> findRunnable(Instant now, Pageable page);
}
