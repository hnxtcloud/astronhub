package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.plugin.service.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import com.iflytek.skillhub.dto.plugin.PluginRecoveryDtos.*;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Joins immutable plan chains and live execution projections without exposing secret material. */
@Repository
@Transactional(readOnly = true)
public class PluginRecoveryQueryRepository {
    private final PluginReleaseWorkflow workflow;
    private final PluginPublicationTaskRepository tasks;
    private final PluginPublicationChannelRepository channels;
    private final PluginChannelPlanRevisionRepository plans;
    private final PluginChannelAttemptRepository attempts;
    private final PluginChannelGovernanceOperationRepository operations;

    public PluginRecoveryQueryRepository(
            PluginReleaseWorkflow workflow,
            PluginPublicationTaskRepository tasks,
            PluginPublicationChannelRepository channels,
            PluginChannelPlanRevisionRepository plans,
            PluginChannelAttemptRepository attempts,
            PluginChannelGovernanceOperationRepository operations) {
        this.workflow = workflow;
        this.tasks = tasks;
        this.channels = channels;
        this.plans = plans;
        this.attempts = attempts;
        this.operations = operations;
    }

    public List<RecoveryChannelView> read(Long releaseId, PluginActor actor) {
        var release = workflow.readRelease(releaseId);
        if (!workflow.canManageRead(workflow.requireProject(release.getProjectId()), actor))
            throw new DomainForbiddenException("error.plugin.permission");
        var task =
                tasks.findByReleaseId(releaseId)
                        .orElseThrow(
                                () -> new DomainBadRequestException("error.plugin.reviewState"));
        var existing = channels.findByTaskIdOrderByChannel(task.getId());
        return Arrays.stream(task.getRequiredChannels().split(","))
                .sorted(Comparator.comparing(c -> c.equals("HOSTED") ? 1 : 0))
                .map(
                        c ->
                                existing.stream()
                                        .filter(v -> v.getChannel().equals(c))
                                        .findFirst()
                                        .map(this::view)
                                        .orElse(
                                                new RecoveryChannelView(
                                                        null,
                                                        c,
                                                        c.equals("HOSTED")
                                                                        && task.hasCompleted(
                                                                                "HOSTED")
                                                                ? "SUCCEEDED"
                                                                : "BLOCKED",
                                                        c.equals("HOSTED")
                                                                        && task.hasCompleted(
                                                                                "HOSTED")
                                                                ? "LEGACY_COMPLETION"
                                                                : "MISSING_CONFIGURATION",
                                                        c.equals("HOSTED")
                                                                        && task.hasCompleted(
                                                                                "HOSTED")
                                                                ? "LEGACY_COMPLETION"
                                                                : "MISSING_CONFIGURATION",
                                                        0,
                                                        false,
                                                        null,
                                                        c.equals("HOSTED")
                                                                        && task.hasCompleted(
                                                                                "HOSTED")
                                                                ? null
                                                                : "MISSING_CONFIGURATION",
                                                        null,
                                                        List.of(),
                                                        List.of(),
                                                        List.of())))
                .toList();
    }

    public RecoveryChannelView view(PluginPublicationChannel c) {
        return new RecoveryChannelView(
                c.getId(),
                c.getChannel(),
                c.getState(),
                c.getSnapshot(),
                c.getEffectiveSnapshot(),
                c.getPlanRevision(),
                c.isSourceDisabled(),
                c.getReceipt(),
                c.getLastError(),
                c.getLeaseUntil(),
                plans.findByChannelIdOrderByRevisionAsc(c.getId()).stream()
                        .map(
                                v ->
                                        new PlanRevisionView(
                                                v.getRevision(),
                                                v.getSnapshot(),
                                                v.getRequestedBy(),
                                                v.getReason(),
                                                v.getCreatedAt()))
                        .toList(),
                attempts
                        .findByChannelIdOrderByIdDesc(
                                c.getId(), org.springframework.data.domain.PageRequest.of(0, 50))
                        .stream()
                        .map(
                                a ->
                                        new AttemptView(
                                                a.getId(),
                                                a.getPlanRevision(),
                                                a.getAction(),
                                                a.getOutcome(),
                                                a.getStartedAt(),
                                                a.getHeartbeatAt(),
                                                a.getLeaseUntil(),
                                                a.getFinishedAt(),
                                                a.getReceipt(),
                                                a.getErrorCode(),
                                                a.getLateReceipt(),
                                                a.getOperationId()))
                        .toList(),
                operations.findByChannelIdOrderByIdDesc(c.getId()).stream()
                        .map(
                                o ->
                                        new GovernanceOperationView(
                                                o.getId(),
                                                o.getAction(),
                                                o.getState(),
                                                o.getPlanRevision(),
                                                o.getSnapshot(),
                                                o.getSourceReceipt(),
                                                o.getRequestedBy(),
                                                o.getReason(),
                                                o.getCreatedAt(),
                                                o.getReceipt(),
                                                o.getLastError()))
                        .toList());
    }

    public AttemptPage attemptHistory(Long releaseId, String channel, int page, PluginActor actor) {
        if (page < 0 || page > 100000)
            throw new DomainBadRequestException("error.plugin.recoveryState");
        var selected =
                read(releaseId, actor).stream()
                        .filter(c -> c.channel().equals(channel))
                        .findFirst()
                        .orElseThrow(
                                () -> new DomainBadRequestException("error.plugin.recoveryState"));
        if (selected.id() == null) return new AttemptPage(List.of(), page, false);
        var rows =
                attempts.findByChannelIdOrderByIdDesc(
                        selected.id(), org.springframework.data.domain.PageRequest.of(page, 50));
        return new AttemptPage(
                rows.stream()
                        .map(
                                a ->
                                        new AttemptView(
                                                a.getId(),
                                                a.getPlanRevision(),
                                                a.getAction(),
                                                a.getOutcome(),
                                                a.getStartedAt(),
                                                a.getHeartbeatAt(),
                                                a.getLeaseUntil(),
                                                a.getFinishedAt(),
                                                a.getReceipt(),
                                                a.getErrorCode(),
                                                a.getLateReceipt(),
                                                a.getOperationId()))
                        .toList(),
                page,
                rows.size() == 50);
    }
}
