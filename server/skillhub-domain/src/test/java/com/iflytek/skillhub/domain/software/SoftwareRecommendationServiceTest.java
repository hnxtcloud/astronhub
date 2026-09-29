package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SoftwareRecommendationServiceTest {

    private final SoftwareRecommendationRepository recommendationRepository =
            mock(SoftwareRecommendationRepository.class);
    private final SoftwarePackageRevisionRepository packageRepository =
            mock(SoftwarePackageRevisionRepository.class);
    private final SoftwareReleaseRepository releaseRepository = mock(SoftwareReleaseRepository.class);
    private final SoftwareRecommendationService service = new SoftwareRecommendationService(
            recommendationRepository, packageRepository, releaseRepository);

    @Test
    void replaceAll_replacesPreviousRecommendations() {
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release()));
        when(packageRepository.findById(5L)).thenReturn(Optional.of(publishedPackage(5L, 10L)));
        when(recommendationRepository.findByReleaseId(10L))
                .thenReturn(List.of(new SoftwareRecommendation(10L, 9L, SoftwareTargetOs.WINDOWS,
                        SoftwareTargetArch.X86_64, null, SoftwarePackageFormat.ZIP, null, "a")));
        when(recommendationRepository.save(any(SoftwareRecommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<SoftwareRecommendation> result = service.replaceAll(10L, List.of(
                new SoftwareRecommendationService.RecommendationRequest(5L, SoftwareTargetOs.WINDOWS,
                        SoftwareTargetArch.X86_64, null, SoftwarePackageFormat.MSI, "recommended")),
                "admin-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFormat()).isEqualTo(SoftwarePackageFormat.MSI);
        verify(recommendationRepository).deleteAllIn(any());
    }

    @Test
    void replaceAll_rejectsDuplicateScopeWithinRequest() {
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release()));
        when(packageRepository.findById(5L)).thenReturn(Optional.of(publishedPackage(5L, 10L)));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.replaceAll(10L, List.of(
                        new SoftwareRecommendationService.RecommendationRequest(5L, SoftwareTargetOs.WINDOWS,
                                SoftwareTargetArch.X86_64, null, SoftwarePackageFormat.MSI, null),
                        new SoftwareRecommendationService.RecommendationRequest(5L, SoftwareTargetOs.WINDOWS,
                                SoftwareTargetArch.X86_64, null, SoftwarePackageFormat.MSI, null)),
                        "admin-1"));
        assertThat(ex.messageCode()).isEqualTo("error.software.recommendation.conflict");
    }

    @Test
    void replaceAll_allowsDifferentFormatsForSameScope() {
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release()));
        when(packageRepository.findById(5L)).thenReturn(Optional.of(publishedPackage(5L, 10L)));
        when(packageRepository.findById(6L)).thenReturn(Optional.of(publishedPackage(6L, 10L)));
        when(recommendationRepository.findByReleaseId(10L)).thenReturn(List.of());
        when(recommendationRepository.save(any(SoftwareRecommendation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<SoftwareRecommendation> result = service.replaceAll(10L, List.of(
                new SoftwareRecommendationService.RecommendationRequest(5L, SoftwareTargetOs.WINDOWS,
                        SoftwareTargetArch.X86_64, null, SoftwarePackageFormat.MSI, null),
                new SoftwareRecommendationService.RecommendationRequest(6L, SoftwareTargetOs.WINDOWS,
                        SoftwareTargetArch.X86_64, null, SoftwarePackageFormat.ZIP, null)),
                "admin-1");

        assertThat(result).hasSize(2);
    }

    @Test
    void replaceAll_rejectsPackageFromAnotherRelease() {
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release()));
        when(packageRepository.findById(7L)).thenReturn(Optional.of(publishedPackage(7L, 99L)));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.replaceAll(10L, List.of(
                        new SoftwareRecommendationService.RecommendationRequest(7L, SoftwareTargetOs.WINDOWS,
                                SoftwareTargetArch.X86_64, null, SoftwarePackageFormat.MSI, null)),
                        "admin-1"));
        assertThat(ex.messageCode()).isEqualTo("error.software.recommendation.releaseMismatch");
    }

    @Test
    void replaceAll_rejectsUnpublishedPackage() {
        when(releaseRepository.findById(10L)).thenReturn(Optional.of(release()));
        when(packageRepository.findById(5L)).thenReturn(Optional.of(draftPackage(5L, 10L)));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.replaceAll(10L, List.of(
                        new SoftwareRecommendationService.RecommendationRequest(5L, SoftwareTargetOs.WINDOWS,
                                SoftwareTargetArch.X86_64, null, SoftwarePackageFormat.MSI, null)),
                        "admin-1"));
        assertThat(ex.messageCode()).isEqualTo("error.software.recommendation.packageNotPublished");
    }

    private SoftwareRelease release() {
        return new SoftwareRelease(1L, "1.0", null, "a");
    }

    private SoftwarePackageRevision publishedPackage(Long id, Long releaseId) {
        SoftwarePackageRevision revision = new SoftwarePackageRevision(releaseId, 1L, 1,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                SoftwarePackageFormat.MSI, "1.0", null, "setup.msi", "a".repeat(64), 1L, "k", "a");
        revision.confirm("admin-1");
        revision.publish();
        return revision;
    }

    private SoftwarePackageRevision draftPackage(Long id, Long releaseId) {
        return new SoftwarePackageRevision(releaseId, 1L, 1,
                SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                SoftwarePackageFormat.MSI, "1.0", null, "setup.msi", "a".repeat(64), 1L, "k", "a");
    }
}
