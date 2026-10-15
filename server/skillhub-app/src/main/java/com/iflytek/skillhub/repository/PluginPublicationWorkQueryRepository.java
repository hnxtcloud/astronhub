package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.plugin.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** Merges bounded eligible work into one fair poll; publication and governance share one worker. */
@Repository
@Transactional(readOnly = true)
public class PluginPublicationWorkQueryRepository {
    private final PluginPublicationTaskRepository tasks;
    private final PluginChannelGovernanceOperationRepository operations;

    public PluginPublicationWorkQueryRepository(
            PluginPublicationTaskRepository tasks,
            PluginChannelGovernanceOperationRepository operations) {
        this.tasks = tasks;
        this.operations = operations;
    }

    public record Work(
            Long releaseId, Long operationId, int attemptCount, Instant createdAt, Long id) {}

    public List<Work> runnable(Instant now) {
        var work = new ArrayList<Work>();
        for (var t : tasks.findRunnable(now, PageRequest.of(0, 20)))
            work.add(
                    new Work(
                            t.getReleaseId(),
                            null,
                            t.getAttemptCount(),
                            t.getCreatedAt(),
                            t.getId()));
        for (var o : operations.findRunnable(now, PageRequest.of(0, 20)))
            work.add(
                    new Work(
                            o.getReleaseId(),
                            o.getId(),
                            o.getAttemptCount(),
                            o.getCreatedAt(),
                            o.getId()));
        return work.stream()
                .sorted(
                        Comparator.comparingInt(Work::attemptCount)
                                .thenComparing(Work::createdAt)
                                .thenComparing(Work::id)
                                .thenComparing(w -> w.operationId() == null ? 0 : 1))
                .limit(20)
                .toList();
    }
}
