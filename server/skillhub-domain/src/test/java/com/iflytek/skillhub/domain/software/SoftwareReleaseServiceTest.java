package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SoftwareReleaseServiceTest {

    private final SoftwareReleaseRepository releaseRepository = mock(SoftwareReleaseRepository.class);
    private final SoftwareApplicationRepository applicationRepository = mock(SoftwareApplicationRepository.class);
    private final SoftwareReleaseService service =
            new SoftwareReleaseService(releaseRepository, applicationRepository);

    @Test
    void create_trimsDisplayVersionAndKeepsCaseVerbatim() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(app()));
        when(releaseRepository.findByApplicationIdAndDisplayVersion(1L, "1.2.0-Beta")).thenReturn(Optional.empty());
        when(releaseRepository.save(any(SoftwareRelease.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SoftwareRelease release = service.create(1L, "  1.2.0-Beta  ", "notes", "admin-1");

        assertThat(release.getDisplayVersion()).isEqualTo("1.2.0-Beta");
    }

    @Test
    void create_rejectsDuplicateDisplayVersionWithinApplication() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(app()));
        when(releaseRepository.findByApplicationIdAndDisplayVersion(1L, "1.0"))
                .thenReturn(Optional.of(new SoftwareRelease(1L, "1.0", null, "a")));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.create(1L, "1.0", null, "admin-1"));
        assertThat(ex.messageCode()).isEqualTo("error.software.release.duplicate");
    }

    @Test
    void create_sameVersionUnderDifferentApplicationsIsAllowed() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(app()));
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(app()));
        when(releaseRepository.findByApplicationIdAndDisplayVersion(1L, "1.0")).thenReturn(Optional.empty());
        when(releaseRepository.save(any(SoftwareRelease.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(1L, "1.0", null, "a").getDisplayVersion()).isEqualTo("1.0");
    }

    @Test
    void create_rejectsBlankVersion() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(app()));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.create(1L, "   ", null, "admin-1"));
        assertThat(ex.messageCode()).isEqualTo("error.software.release.versionRequired");
    }

    @Test
    void create_rejectsUnknownApplication() {
        when(applicationRepository.findById(99L)).thenReturn(Optional.empty());

        com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException ex =
                assertThrows(com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException.class,
                        () -> service.create(99L, "1.0", null, "admin-1"));
        assertThat(ex.messageCode()).isEqualTo("error.software.app.notFound");
    }

    private SoftwareApplication app() {
        return new SoftwareApplication("vscode", "VS Code", null, null, null, "a");
    }
}
