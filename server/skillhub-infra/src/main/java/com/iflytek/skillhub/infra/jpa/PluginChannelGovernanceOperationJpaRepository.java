package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.plugin.*;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

import java.time.Instant;
import java.util.*;

public interface PluginChannelGovernanceOperationJpaRepository
        extends JpaRepository<PluginChannelGovernanceOperation, Long>,
                PluginChannelGovernanceOperationRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PluginChannelGovernanceOperation o where o.id=:id")
    Optional<PluginChannelGovernanceOperation> findByIdForUpdate(Long id);

    @Query(
            "select o from PluginChannelGovernanceOperation o where (o.state in"
                + " ('QUEUED','FAILED','UNKNOWN') or (o.state='RUNNING' and o.leaseUntil<=:now))"
                + " and (o.nextAttemptAt is null or o.nextAttemptAt<=:now) order by"
                + " o.attemptCount,o.createdAt,o.id")
    List<PluginChannelGovernanceOperation> findRunnable(Instant now, Pageable page);
}
