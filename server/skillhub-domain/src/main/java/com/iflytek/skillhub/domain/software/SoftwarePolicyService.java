package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the versioned software runtime policy: reading the authoritative active
 * snapshot, writing validated new revisions with optimistic concurrency, and
 * rollback via new revisions. PostgreSQL is the single source of truth; the
 * pointer switch and revision insert always share one transaction.
 */
@Service
public class SoftwarePolicyService {

    private final SoftwarePolicyRevisionRepository revisionRepository;
    private final SoftwarePolicyStateRepository stateRepository;
    private final SoftwarePolicyDeploymentLimits deploymentLimits;

    public SoftwarePolicyService(SoftwarePolicyRevisionRepository revisionRepository,
                                 SoftwarePolicyStateRepository stateRepository,
                                 SoftwarePolicyDeploymentLimits deploymentLimits) {
        this.revisionRepository = revisionRepository;
        this.stateRepository = stateRepository;
        this.deploymentLimits = deploymentLimits;
    }

    /** Returns the active snapshot or null when policy has not been seeded yet. */
    @Transactional(readOnly = true)
    public SoftwarePolicySnapshot findActive() {
        SoftwarePolicyRevision revision = findActiveRevision();
        return revision == null ? null : toSnapshot(revision);
    }

    /**
     * Returns the active snapshot for admission transactions. A missing policy
     * is a hard failure: admissions must never fall back to lenient defaults.
     */
    @Transactional(readOnly = true)
    public SoftwarePolicySnapshot requireActive() {
        SoftwarePolicyRevision revision = findActiveRevision();
        if (revision == null) {
            throw new DomainBadRequestException("error.software.settings.notInitialized");
        }
        return toSnapshot(revision);
    }

    /**
     * Validates and persists a new policy revision. Fails with 409 when
     * {@code expectedRevision} does not match the active revision, so
     * concurrent edits never overwrite each other silently.
     */
    @Transactional
    public SoftwarePolicySnapshot update(Long expectedRevision,
                                         SoftwarePolicyConfig newConfig,
                                         String changedBy,
                                         String changeReason) {
        requireReason(changeReason);
        SoftwarePolicyState state = requireState();
        SoftwarePolicyRevision current = revisionRepository.findById(state.getActiveRevisionId())
                .orElseThrow(() -> new DomainBadRequestException("error.software.settings.notInitialized"));

        if (expectedRevision == null) {
            throw new DomainBadRequestException("error.software.settings.expectedRevisionRequired");
        }
        if (current.getRevision() != expectedRevision) {
            throw new DomainConflictException("error.software.settings.revisionConflict",
                    expectedRevision, current.getRevision());
        }

        requireValid(newConfig);
        return persistNewRevision(newConfig, changedBy, changeReason, current.getRevision() + 1, state);
    }

    /** Validates a candidate configuration without persisting anything. */
    @Transactional(readOnly = true)
    public List<SoftwarePolicyConfig.Violation> validate(SoftwarePolicyConfig candidate) {
        return SoftwarePolicyValidator.validate(candidate, deploymentLimits);
    }

    /**
     * Creates a new revision from a historical revision's values after
     * validating them against the current deployment limits. Business data is
     * never rolled back by this operation.
     */
    @Transactional
    public SoftwarePolicySnapshot rollback(long fromRevision, String changedBy, String changeReason) {
        requireReason(changeReason);
        SoftwarePolicyState state = requireState();
        SoftwarePolicyRevision historical = revisionRepository.findByRevision(fromRevision)
                .orElseThrow(() -> new DomainNotFoundException("error.software.settings.revisionNotFound",
                        fromRevision));
        SoftwarePolicyConfig historicalConfig = SoftwarePolicyConfigJson.fromJson(historical.getConfig());
        requireValid(historicalConfig);
        return persistNewRevision(historicalConfig, changedBy, changeReason,
                currentRevision(state) + 1, state);
    }

    @Transactional(readOnly = true)
    public Page<SoftwarePolicyRevision> history(Pageable pageable) {
        return revisionRepository.findAllByOrderByRevisionDesc(pageable);
    }

    private SoftwarePolicySnapshot persistNewRevision(SoftwarePolicyConfig config,
                                                      String changedBy,
                                                      String changeReason,
                                                      long nextRevision,
                                                      SoftwarePolicyState state) {
        SoftwarePolicyRevision revision = revisionRepository.save(new SoftwarePolicyRevision(
                nextRevision,
                SoftwarePolicyConfig.SCHEMA_VERSION,
                SoftwarePolicyConfigJson.toJson(config),
                changedBy,
                changeReason
        ));
        state.setActiveRevisionId(revision.getId());
        stateRepository.save(state);
        return toSnapshot(revision);
    }

    private long currentRevision(SoftwarePolicyState state) {
        return revisionRepository.findById(state.getActiveRevisionId())
                .orElseThrow(() -> new DomainBadRequestException("error.software.settings.notInitialized"))
                .getRevision();
    }

    private SoftwarePolicyRevision findActiveRevision() {
        return stateRepository.findSingleton()
                .flatMap(state -> revisionRepository.findById(state.getActiveRevisionId()))
                .orElse(null);
    }

    private SoftwarePolicyState requireState() {
        return stateRepository.findSingleton()
                .orElseThrow(() -> new DomainBadRequestException("error.software.settings.notInitialized"));
    }

    private SoftwarePolicySnapshot toSnapshot(SoftwarePolicyRevision revision) {
        return new SoftwarePolicySnapshot(
                revision.getRevision(),
                revision.getSchemaVersion(),
                SoftwarePolicyConfigJson.fromJson(revision.getConfig()),
                revision.getChangedBy(),
                revision.getCreatedAt()
        );
    }

    private void requireValid(SoftwarePolicyConfig config) {
        List<SoftwarePolicyConfig.Violation> violations =
                SoftwarePolicyValidator.validate(config, deploymentLimits);
        if (!violations.isEmpty()) {
            throw new DomainBadRequestException("error.software.settings.invalid",
                    violations.get(0).field());
        }
    }

    private void requireReason(String changeReason) {
        if (changeReason == null || changeReason.isBlank()) {
            throw new DomainBadRequestException("error.software.settings.reasonRequired");
        }
    }
}
