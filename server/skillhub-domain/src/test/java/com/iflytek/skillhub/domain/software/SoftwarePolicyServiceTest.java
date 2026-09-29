package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SoftwarePolicyServiceTest {

    private final SoftwarePolicyRevisionRepository revisionRepository =
            mock(SoftwarePolicyRevisionRepository.class);
    private final SoftwarePolicyStateRepository stateRepository = mock(SoftwarePolicyStateRepository.class);
    private final SoftwarePolicyDeploymentLimits limits = new SoftwarePolicyDeploymentLimits(
            100L * 1024 * 1024, 1024L * 1024 * 1024, 512L * 1024 * 1024);
    private final SoftwarePolicyService service =
            new SoftwarePolicyService(revisionRepository, stateRepository, limits);

    @Test
    void requireActive_failsWhenPolicyUnseeded() {
        when(stateRepository.findSingleton()).thenReturn(Optional.empty());

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                service::requireActive);
        assertThat(ex.messageCode()).isEqualTo("error.software.settings.notInitialized");
    }

    @Test
    void update_rejectsStaleExpectedRevisionWithConflict() {
        SoftwarePolicyState state = new SoftwarePolicyState(11L);
        when(stateRepository.findSingleton()).thenReturn(Optional.of(state));
        // Active revision is already 2; a client holding revision 1 is stale.
        SoftwarePolicyRevision current = revision(2, 11L, SoftwarePolicyConfig.defaults());
        when(revisionRepository.findById(11L)).thenReturn(Optional.of(current));

        DomainConflictException ex = assertThrows(DomainConflictException.class,
                () -> service.update(1L, SoftwarePolicyConfig.defaults(), "admin-1", "tune"));
        assertThat(ex.messageCode()).isEqualTo("error.software.settings.revisionConflict");
        assertThat(ex.statusCode()).isEqualTo(409);
    }

    @Test
    void update_persistsNewRevisionAndSwitchesPointerAtomically() {
        SoftwarePolicyState state = new SoftwarePolicyState(11L);
        when(stateRepository.findSingleton()).thenReturn(Optional.of(state));
        when(revisionRepository.findById(11L)).thenReturn(Optional.of(revision(2, 11L,
                SoftwarePolicyConfig.defaults())));
        when(revisionRepository.save(any(SoftwarePolicyRevision.class)))
                .thenAnswer(invocation -> {
                    SoftwarePolicyRevision saved = invocation.getArgument(0);
                    assignId(saved, 21L);
                    return saved;
                });
        when(stateRepository.save(any(SoftwarePolicyState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SoftwarePolicyConfig changed = new SoftwarePolicyConfig(4L * 1024 * 1024 * 1024,
                400L * 1024 * 1024 * 1024, 2, 8L * 1024 * 1024, 1440, 168, 5, 1, 900, 60,
                512L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);
        SoftwarePolicySnapshot snapshot = service.update(2L, changed, "admin-1", "调低配额");

        assertThat(snapshot.revision()).isEqualTo(3L);
        assertThat(snapshot.config().maxPackageBytes()).isEqualTo(4L * 1024 * 1024 * 1024);
        assertThat(state.getActiveRevisionId()).isNotNull();
    }

    @Test
    void update_requiresChangeReason() {
        SoftwarePolicyState state = new SoftwarePolicyState(11L);
        when(stateRepository.findSingleton()).thenReturn(Optional.of(state));
        when(revisionRepository.findById(11L)).thenReturn(Optional.of(revision(2, 11L,
                SoftwarePolicyConfig.defaults())));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.update(1L, SoftwarePolicyConfig.defaults(), "admin-1", "  "));
        assertThat(ex.messageCode()).isEqualTo("error.software.settings.reasonRequired");
    }

    @Test
    void update_rejectsInvalidConfigurationWithoutWriting() {
        SoftwarePolicyState state = new SoftwarePolicyState(11L);
        when(stateRepository.findSingleton()).thenReturn(Optional.of(state));
        when(revisionRepository.findById(11L)).thenReturn(Optional.of(revision(2, 11L,
                SoftwarePolicyConfig.defaults())));

        SoftwarePolicyConfig invalid = new SoftwarePolicyConfig(600L * 1024 * 1024 * 1024,
                500L * 1024 * 1024 * 1024, 2, 8L * 1024 * 1024, 1440, 168, 5, 1, 900, 900,
                512L * 1024 * 1024, 20L * 1024 * 1024 * 1024, 10_000, 3, 1440);
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.update(2L, invalid, "admin-1", "invalid change"));
        assertThat(ex.messageCode()).isEqualTo("error.software.settings.invalid");
    }

    @Test
    void rollback_createsNewRevisionFromHistoricalValues() {
        SoftwarePolicyConfig historicalConfig = SoftwarePolicyConfig.defaults();
        SoftwarePolicyState state = new SoftwarePolicyState(12L);
        when(stateRepository.findSingleton()).thenReturn(Optional.of(state));
        when(revisionRepository.findByRevision(1L)).thenReturn(Optional.of(revision(1, 11L, historicalConfig)));
        when(revisionRepository.findById(12L)).thenReturn(Optional.of(revision(3, 12L,
                new SoftwarePolicyConfig(1L * 1024 * 1024 * 1024, 500L * 1024 * 1024 * 1024, 2,
                        8L * 1024 * 1024, 1440, 168, 5, 1, 900, 60, 512L * 1024 * 1024,
                        20L * 1024 * 1024 * 1024, 10_000, 3, 1440))));
        when(revisionRepository.save(any(SoftwarePolicyRevision.class)))
                .thenAnswer(invocation -> {
                    SoftwarePolicyRevision saved = invocation.getArgument(0);
                    assignId(saved, 22L);
                    return saved;
                });
        when(stateRepository.save(any(SoftwarePolicyState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SoftwarePolicySnapshot snapshot = service.rollback(1L, "admin-1", "回滚到默认值");

        assertThat(snapshot.revision()).isEqualTo(4L);
        assertThat(snapshot.config()).isEqualTo(historicalConfig);
    }

    @Test
    void rollback_rejectsUnknownRevision() {
        SoftwarePolicyState state = new SoftwarePolicyState(12L);
        when(stateRepository.findSingleton()).thenReturn(Optional.of(state));
        when(revisionRepository.findByRevision(99L)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.rollback(99L, "admin-1", "回滚"));
    }

    @Test
    void findActive_returnsNullWhenUnseeded() {
        when(stateRepository.findSingleton()).thenReturn(Optional.empty());
        assertThat(service.findActive()).isNull();
    }

    private SoftwarePolicyRevision revision(long revisionNumber, Long id, SoftwarePolicyConfig config) {
        SoftwarePolicyRevision revision = new SoftwarePolicyRevision(revisionNumber,
                SoftwarePolicyConfig.SCHEMA_VERSION, SoftwarePolicyConfigJson.toJson(config),
                "admin-1", "seed");
        assignId(revision, id);
        return revision;
    }

    private static void assignId(SoftwarePolicyRevision revision, Long id) {
        try {
            java.lang.reflect.Field field = SoftwarePolicyRevision.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(revision, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to assign test id", ex);
        }
    }
}
