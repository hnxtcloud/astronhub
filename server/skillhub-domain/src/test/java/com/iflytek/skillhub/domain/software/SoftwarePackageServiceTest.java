package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SoftwarePackageServiceTest {

    private static final String SHA = "a".repeat(64);

    private final SoftwarePackageRevisionRepository packageRepository =
            mock(SoftwarePackageRevisionRepository.class);
    private final SoftwareRecommendationRepository recommendationRepository =
            mock(SoftwareRecommendationRepository.class);
    private final SoftwareReleaseRepository releaseRepository = mock(SoftwareReleaseRepository.class);
    private final SoftwareInspectionTaskRepository inspectionTaskRepository =
            mock(SoftwareInspectionTaskRepository.class);
    private final SoftwarePackageService service = new SoftwarePackageService(
            packageRepository, recommendationRepository, releaseRepository, inspectionTaskRepository);

    @Test
    void register_assignsRevisionNoWithinSameTargetScope() {
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(new SoftwareRelease(1L, "1.0", null, "a")));
        when(packageRepository.existsByApplicationIdAndSha256(1L, SHA)).thenReturn(false);
        when(packageRepository.findByReleaseIdOrderByIdDesc(10L)).thenReturn(List.of(
                revision(10L, 1, SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, "1.0.0")));
        when(packageRepository.save(any(SoftwarePackageRevision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SoftwarePackageRevision revision = service.register(10L, 1L,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                SoftwarePackageFormat.MSI, "1.0.0", null, "setup.msi", SHA, 1024L, "sw/obj", "admin-1");

        assertThat(revision.getRevisionNo()).isEqualTo(2);
        assertThat(revision.getStatus()).isEqualTo(SoftwarePackageStatus.DRAFT);
    }

    @Test
    void register_sameFullVersionDifferentDistroStartsOwnSequence() {
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(new SoftwareRelease(1L, "1.0", null, "a")));
        when(packageRepository.existsByApplicationIdAndSha256(1L, SHA)).thenReturn(false);
        when(packageRepository.findByReleaseIdOrderByIdDesc(10L)).thenReturn(List.of(
                revision(10L, 3, SoftwareTargetOs.LINUX, SoftwareTargetArch.X86_64, "centos7", "1.0.0-1.el7")));
        when(packageRepository.save(any(SoftwarePackageRevision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SoftwarePackageRevision revision = service.register(10L, 1L,
                SoftwareTargetOs.LINUX, SoftwareTargetArch.X86_64, "centos9",
                null, SoftwarePackageFormat.RPM, "1.0.0-1.el9", null, "app.rpm", SHA, 1024L, "sw/obj", "a");

        assertThat(revision.getRevisionNo()).isEqualTo(1);
    }

    @Test
    void register_rejectsDuplicateSha256WithinApplication() {
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(new SoftwareRelease(1L, "1.0", null, "a")));
        when(packageRepository.existsByApplicationIdAndSha256(1L, SHA)).thenReturn(true);

        DomainConflictException ex = assertThrows(DomainConflictException.class,
                () -> service.register(10L, 1L, SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64,
                        null, null, SoftwarePackageFormat.MSI, "1.0.0", null, "setup.msi", SHA, 1024L,
                        "sw/obj", "a"));
        assertThat(ex.messageCode()).isEqualTo("error.software.package.duplicateSha256");
        assertThat(ex.statusCode()).isEqualTo(409);
    }

    @Test
    void register_rejectsInvalidSha256AndSize() {
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(new SoftwareRelease(1L, "1.0", null, "a")));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.register(10L, 1L, SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64,
                        null, null, SoftwarePackageFormat.MSI, "1.0.0", null, "setup.msi", "short", 1024L,
                        "sw/obj", "a"));
        assertThat(ex.messageCode()).isEqualTo("error.software.package.sha256Invalid");

        ex = assertThrows(DomainBadRequestException.class,
                () -> service.register(10L, 1L, SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64,
                        null, null, SoftwarePackageFormat.MSI, "1.0.0", null, "setup.msi", SHA, 0L,
                        "sw/obj", "a"));
        assertThat(ex.messageCode()).isEqualTo("error.software.package.byteSizeInvalid");
    }

    @Test
    void publish_requiresAdministratorConfirmation() {
        SoftwarePackageRevision revision = revision(10L, 1, SoftwareTargetOs.WINDOWS,
                SoftwareTargetArch.X86_64, null, "1.0.0");
        when(packageRepository.findById(5L)).thenReturn(Optional.of(revision));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.publish(5L));
        assertThat(ex.messageCode()).isEqualTo("error.software.package.confirmationRequired");
    }

    @Test
    void confirmThenPublish_movesDraftToPublished() {
        SoftwarePackageRevision revision = revision(10L, 1, SoftwareTargetOs.WINDOWS,
                SoftwareTargetArch.X86_64, null, "1.0.0");
        when(packageRepository.findById(5L)).thenReturn(Optional.of(revision));
        when(packageRepository.save(any(SoftwarePackageRevision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.confirm(5L, "admin-1");
        assertThat(revision.isConfirmed()).isTrue();

        service.publish(5L);
        assertThat(revision.getStatus()).isEqualTo(SoftwarePackageStatus.PUBLISHED);
        assertThat(revision.getPublishedAt()).isNotNull();
    }

    @Test
    void takeDown_clearsRecommendationsReferencingThePackage() {
        SoftwarePackageRevision revision = revision(10L, 1, SoftwareTargetOs.WINDOWS,
                SoftwareTargetArch.X86_64, null, "1.0.0");
        revision.confirm("admin-1");
        revision.publish();
        when(packageRepository.findById(5L)).thenReturn(Optional.of(revision));
        when(packageRepository.save(any(SoftwarePackageRevision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SoftwareRecommendation recommendation = new SoftwareRecommendation(10L, 5L,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, SoftwarePackageFormat.MSI,
                null, "admin-1");
        when(recommendationRepository.findByPackageRevisionId(5L)).thenReturn(List.of(recommendation));

        service.takeDown(5L);

        assertThat(revision.getStatus()).isEqualTo(SoftwarePackageStatus.TAKEN_DOWN);
        verify(recommendationRepository).deleteAllIn(List.of(recommendation));
    }

    @Test
    void restore_requiresTakenDownState() {
        SoftwarePackageRevision revision = revision(10L, 1, SoftwareTargetOs.WINDOWS,
                SoftwareTargetArch.X86_64, null, "1.0.0");
        when(packageRepository.findById(5L)).thenReturn(Optional.of(revision));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.restore(5L));
        assertThat(ex.messageCode()).isEqualTo("error.software.package.invalidTransition");
        verify(packageRepository, never()).save(any());
    }

    @Test
    void register_rejectsUnknownRelease() {
        when(releaseRepository.findById(99L)).thenReturn(Optional.empty());

        com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException ex =
                assertThrows(com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException.class,
                        () -> service.register(99L, 1L, SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64,
                                null, null, SoftwarePackageFormat.MSI, "1.0.0", null, "s.msi", SHA, 1L,
                                "k", "a"));
        assertThat(ex.messageCode()).isEqualTo("error.software.release.notFound");
    }

    private SoftwarePackageRevision revision(Long releaseId, int revisionNo, SoftwareTargetOs os,
                                            SoftwareTargetArch arch, String distro, String fullVersion) {
        return new SoftwarePackageRevision(releaseId, 1L, revisionNo, os, arch, distro, null,
                SoftwarePackageFormat.MSI, fullVersion, null, "setup.msi",
                "a".repeat(64), 1024L, "sw/obj", "admin-1");
    }

    @Test
    void confirm_blockedWhileBasicInspectionFailed() {
        SoftwarePackageRevision revision = revision(10L, 1, SoftwareTargetOs.WINDOWS,
                SoftwareTargetArch.X86_64, null, "1.0.0");
        when(packageRepository.findById(10L)).thenReturn(Optional.of(revision));
        SoftwareInspectionTask failedTask = task(10L, SoftwareInspectionTaskStatus.BASIC_FAILED);
        when(inspectionTaskRepository.findByPackageRevisionId(10L)).thenReturn(Optional.of(failedTask));

        assertThrows(DomainConflictException.class, () -> service.confirm(10L, "admin-1"));
        assertThat(revision.isConfirmed()).isFalse();
    }

    @Test
    void confirm_blockedUntilMetadataFailureIsFilledIn() {
        SoftwarePackageRevision revision = revision(10L, 1, SoftwareTargetOs.WINDOWS,
                SoftwareTargetArch.X86_64, null, "1.0.0");
        when(packageRepository.findById(10L)).thenReturn(Optional.of(revision));
        when(inspectionTaskRepository.findByPackageRevisionId(10L))
                .thenReturn(Optional.of(task(10L, SoftwareInspectionTaskStatus.METADATA_FAILED)));

        var ex = assertThrows(DomainConflictException.class, () -> service.confirm(10L, "admin-1"));
        assertThat(ex.messageCode()).isEqualTo("error.software.package.metadataNotResolved");
        assertThat(revision.isConfirmed()).isFalse();

        // the manual fill-in travels with the confirmation and resolves the failure
        when(packageRepository.save(any(SoftwarePackageRevision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service.confirm(10L, "admin-1", "{\"ProductVersion\":\"9.9.9\"}");
        assertThat(revision.getRawVersionFields()).contains("9.9.9");
        assertThat(revision.isConfirmed()).isTrue();

        // re-confirm without fields stays allowed once a human has confirmed
        service.confirm(10L, "admin-1");
        assertThat(revision.isConfirmed()).isTrue();
    }

    @Test
    void confirm_blockedWhileRequiredInspectionIncomplete() {
        for (SoftwareInspectionTaskStatus incomplete : List.of(
                SoftwareInspectionTaskStatus.PENDING, SoftwareInspectionTaskStatus.LEASED,
                SoftwareInspectionTaskStatus.EXHAUSTED)) {
            SoftwarePackageRevision revision = revision(10L, 1, SoftwareTargetOs.WINDOWS,
                    SoftwareTargetArch.X86_64, null, "1.0.0");
            when(packageRepository.findById(10L)).thenReturn(Optional.of(revision));
            when(inspectionTaskRepository.findByPackageRevisionId(10L))
                    .thenReturn(Optional.of(task(10L, incomplete)));

            // even a manual fill-in must not bypass unfinished validation
            var ex = assertThrows(DomainConflictException.class,
                    () -> service.confirm(10L, "admin-1", "{\"ProductVersion\":\"1.0.0\"}"));
            assertThat(ex.messageCode()).isEqualTo("error.software.package.inspectionIncomplete");
            assertThat(revision.isConfirmed()).isFalse();
        }
    }

    /** Builds an unclaimed task for {@code packageRevisionId} and drives it to {@code status}. */
    private SoftwareInspectionTask task(Long packageRevisionId, SoftwareInspectionTaskStatus status) {
        SoftwarePolicySnapshot snapshot =
                new SoftwarePolicySnapshot(1, 1, SoftwarePolicyConfig.defaults(), "a", Instant.EPOCH);
        SoftwareInspectionTask t = new SoftwareInspectionTask(
                packageRevisionId, 1L, 3, snapshot, "sw/obj", SoftwarePackageFormat.MSI);
        switch (status) {
            case BASIC_FAILED -> t.applyOutcome(SoftwareInspectionOutcome.BASIC_FAILED, Instant.EPOCH, null);
            case METADATA_FAILED ->
                    t.applyOutcome(SoftwareInspectionOutcome.METADATA_FAILED, Instant.EPOCH, null);
            case SUCCEEDED -> t.applyOutcome(SoftwareInspectionOutcome.SUCCEEDED, Instant.EPOCH, null);
            case LEASED -> t.claim("w", "tok", snapshot, Instant.EPOCH, Instant.EPOCH.plusSeconds(60));
            case EXHAUSTED -> {
                t.claim("w", "tok", snapshot, Instant.EPOCH, Instant.EPOCH.plusSeconds(60));
                t.exhaustFromExpiredLease(Instant.EPOCH);
            }
            case PENDING -> {
                // fresh task is born pending
            }
        }
        return t;
    }

    @Test
    void confirm_acceptsFillInOnlyWhenExtractionProducedNothing() {
        SoftwarePackageRevision empty = revision(10L, 1, SoftwareTargetOs.WINDOWS,
                SoftwareTargetArch.X86_64, null, "1.0.0");
        when(packageRepository.findById(10L)).thenReturn(Optional.of(empty));
        when(inspectionTaskRepository.findByPackageRevisionId(10L)).thenReturn(Optional.empty());
        when(packageRepository.save(any(SoftwarePackageRevision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.confirm(10L, "admin-1", "{\"Version\":\"2.0\"}");
        assertThat(empty.getRawVersionFields()).contains("2.0");
        assertThat(empty.isConfirmed()).isTrue();

        SoftwarePackageRevision extracted = revision(11L, 1, SoftwareTargetOs.WINDOWS,
                SoftwareTargetArch.X86_64, null, "1.0.0");
        // simulate already-extracted fields: fill-in must be rejected
        extracted.fillInRawVersionFields("{\"From\":\"worker\"}");
        when(packageRepository.findById(11L)).thenReturn(Optional.of(extracted));
        assertThrows(DomainConflictException.class,
                () -> service.confirm(11L, "admin-1", "{\"Version\":\"3.0\"}"));
    }
}
