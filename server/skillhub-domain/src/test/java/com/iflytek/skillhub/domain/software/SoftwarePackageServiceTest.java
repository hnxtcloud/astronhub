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
    private final SoftwarePackageService service = new SoftwarePackageService(
            packageRepository, recommendationRepository, releaseRepository);

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
}
