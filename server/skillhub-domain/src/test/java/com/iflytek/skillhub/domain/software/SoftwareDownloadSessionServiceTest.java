package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SoftwareDownloadSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");

    private SoftwareDownloadSessionRepository sessionRepository;
    private SoftwarePackageRevisionRepository packageRepository;
    private SoftwareApplicationRepository applicationRepository;
    private SoftwarePolicyService policyService;
    private SoftwareDownloadSessionService service;
    private SoftwarePackageRevision publishedRevision;
    private SoftwareApplication publishedApp;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SoftwareDownloadSessionRepository.class);
        packageRepository = mock(SoftwarePackageRevisionRepository.class);
        applicationRepository = mock(SoftwareApplicationRepository.class);
        policyService = mock(SoftwarePolicyService.class);
        service = new SoftwareDownloadSessionService(sessionRepository, packageRepository,
                applicationRepository, policyService);

        when(policyService.requireActive()).thenReturn(new SoftwarePolicySnapshot(
                1, 1, SoftwarePolicyConfig.defaults(), "system", NOW));
        publishedApp = new SoftwareApplication("my-app", "My App", "desc", null, "cat", "admin-1");
        publishedApp.publish();
        publishedRevision = new SoftwarePackageRevision(
                10L, 1L, 1, SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                SoftwarePackageFormat.MSI, "1.0.0.0", null, "app.msi",
                "a".repeat(64), 1024, "software/packages/x/app.msi", "admin-1");
        publishedRevision.confirm("admin-1");
        publishedRevision.publish();

        when(packageRepository.findById(5L)).thenReturn(Optional.of(publishedRevision));
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(publishedApp));
        when(sessionRepository.save(org.mockito.ArgumentMatchers.any(SoftwareDownloadSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createSession_issuesBoundSessionForPublishedPackage() {
        SoftwareDownloadSessionService.IssuedDownloadSession issued =
                service.createSession(5L, "user-1", NOW);

        assertThat(issued.session().getUserId()).isEqualTo("user-1");
        assertThat(issued.session().getPackageRevisionId()).isEqualTo(5L);
        assertThat(issued.session().isCounted()).isTrue();
        assertThat(issued.session().getExpiresAt()).isEqualTo(NOW.plusSeconds(24 * 3600));
        assertThat(issued.revision()).isSameAs(publishedRevision);
    }

    @Test
    void createSession_rejectsDraftOrTakenDownPackagesAndApps() {
        assertThatThrownBy(() -> service.createSession(404L, "user-1", NOW))
                .isInstanceOf(DomainNotFoundException.class);

        SoftwarePackageRevision draft = new SoftwarePackageRevision(
                10L, 1L, 2, SoftwareTargetOs.WINDOWS, SoftwareTargetArch.X86_64, null, null,
                SoftwarePackageFormat.MSI, "1.0.0.1", null, "app2.msi",
                "b".repeat(64), 1024, "k", "admin-1");
        when(packageRepository.findById(6L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.createSession(6L, "user-1", NOW))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.software.download.notPublished");

        publishedApp.takeDown();
        assertThatThrownBy(() -> service.createSession(5L, "user-1", NOW))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.software.download.notPublished");
        publishedApp.restore();
    }

    @Test
    void openForDownload_revalidatesOwnerExpiryAndPublication() {
        SoftwareDownloadSession session = new SoftwareDownloadSession(
                "s-1", "user-1", 5L, true, 1440, NOW);
        when(sessionRepository.findById("s-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.openForDownload("s-1", "user-2", NOW))
                .isInstanceOf(DomainNotFoundException.class);

        assertThatThrownBy(() -> service.openForDownload("s-1", "user-1",
                NOW.plusSeconds(25 * 3600)))
                .isInstanceOf(DomainNotFoundException.class)
                .hasMessageContaining("error.software.download.sessionExpired");

        SoftwareDownloadSessionService.ActiveDownload active =
                service.openForDownload("s-1", "user-1", NOW.plusSeconds(60));
        assertThat(active.session().getLastActivityAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(active.revision()).isSameAs(publishedRevision);

        publishedRevision.takeDown();
        assertThatThrownBy(() -> service.openForDownload("s-1", "user-1", NOW.plusSeconds(120)))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.software.download.notPublished");
    }

    @Test
    void openForDownload_unknownSessionFails() {
        when(sessionRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.openForDownload("missing", "user-1", NOW))
                .isInstanceOf(DomainNotFoundException.class);
    }
}
