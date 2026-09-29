package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SoftwareApplicationServiceTest {

    private final SoftwareApplicationRepository repository = mock(SoftwareApplicationRepository.class);
    private final SoftwareApplicationService service = new SoftwareApplicationService(repository);

    @Test
    void create_normalizesSlugAndStartsAsDraft() {
        when(repository.existsBySlug("visual-studio-code")).thenReturn(false);
        when(repository.save(any(SoftwareApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SoftwareApplication app = service.create("  Visual-Studio-Code ", "Visual Studio Code",
                "Editor", null, "dev-tools", "admin-1");

        assertThat(app.getSlug()).isEqualTo("visual-studio-code");
        assertThat(app.getStatus()).isEqualTo(SoftwareApplicationStatus.DRAFT);
        assertThat(app.getName()).isEqualTo("Visual Studio Code");
    }

    @Test
    void create_rejectsDuplicateSlug() {
        when(repository.existsBySlug("vscode")).thenReturn(true);

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.create("vscode", "VS Code", null, null, null, "admin-1"));
        assertThat(ex.messageCode()).isEqualTo("error.software.slug.duplicate");
    }

    @Test
    void create_rejectsBlankName() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.create("vscode", "   ", null, null, null, "admin-1"));
        assertThat(ex.messageCode()).isEqualTo("error.software.app.nameRequired");
        verify(repository, never()).save(any());
    }

    @Test
    void publish_movesDraftToPublished() {
        SoftwareApplication app = new SoftwareApplication("vscode", "VS Code", null, null, null, "a");
        when(repository.findById(1L)).thenReturn(Optional.of(app));
        when(repository.save(any(SoftwareApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.publish(1L);

        assertThat(app.getStatus()).isEqualTo(SoftwareApplicationStatus.PUBLISHED);
    }

    @Test
    void takeDown_requiresPublishedState() {
        SoftwareApplication app = new SoftwareApplication("vscode", "VS Code", null, null, null, "a");
        when(repository.findById(1L)).thenReturn(Optional.of(app));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.takeDown(1L));
        assertThat(ex.messageCode()).isEqualTo("error.software.app.invalidTransition");
    }

    @Test
    void restore_requiresTakenDownState() {
        SoftwareApplication app = new SoftwareApplication("vscode", "VS Code", null, null, null, "a");
        app.publish();
        when(repository.findById(1L)).thenReturn(Optional.of(app));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.restore(1L));
        assertThat(ex.messageCode()).isEqualTo("error.software.app.invalidTransition");
    }

    @Test
    void restore_returnsApplicationToPublished() {
        SoftwareApplication app = new SoftwareApplication("vscode", "VS Code", null, null, null, "a");
        app.publish();
        app.takeDown();
        when(repository.findById(1L)).thenReturn(Optional.of(app));
        when(repository.save(any(SoftwareApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.restore(1L);

        assertThat(app.getStatus()).isEqualTo(SoftwareApplicationStatus.PUBLISHED);
    }

    @Test
    void requireVisibleApplication_hidesUnpublishedFromPortal() {
        SoftwareApplication draft = new SoftwareApplication("vscode", "VS Code", null, null, null, "a");
        when(repository.findBySlug("vscode")).thenReturn(Optional.of(draft));

        com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException ex =
                assertThrows(com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException.class,
                        () -> service.requireVisibleApplication("vscode"));
        assertThat(ex.messageCode()).isEqualTo("error.software.app.notFound");
        assertThat(ex.statusCode()).isEqualTo(404);
    }
}
