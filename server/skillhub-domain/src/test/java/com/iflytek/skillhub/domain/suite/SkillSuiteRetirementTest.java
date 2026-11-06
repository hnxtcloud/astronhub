package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillSuiteRetirementTest {
    @Test
    void freezesOrdinaryDraftButPublishesAnEligibleLegacyDraft() throws Exception {
        SkillSuite suite = new SkillSuite(1L, "writers", "Writers", "author");
        setId(suite, 7L);
        SkillSuiteVersion version = new SkillSuiteVersion(7L, "1.0.0", SkillVisibility.PRIVATE, "author");
        setId(version, 8L);
        SkillSuiteRepository suites = mock(SkillSuiteRepository.class);
        SkillSuiteVersionRepository versions = mock(SkillSuiteVersionRepository.class);
        NamespaceRepository namespaces = mock(NamespaceRepository.class);
        when(suites.findById(7L)).thenReturn(Optional.of(suite));
        when(versions.findById(8L)).thenReturn(Optional.of(version));
        when(namespaces.findById(1L)).thenReturn(Optional.of(new Namespace("team", "Team", "admin")));
        SkillSuiteLifecycleService service = new SkillSuiteLifecycleService(
                suites, versions, mock(ReviewTaskRepository.class), new ReviewPermissionChecker(),
                namespaces, mock(SkillSuitePublicationValidator.class), mock(AuditLogService.class),
                Clock.systemUTC(), true, new SkillSuiteRetirementPolicy(true));
        SkillSuiteActionContext actor = new SkillSuiteActionContext(
                "admin", Map.of(), Set.of("SUPER_ADMIN"), "retirement-test", null, null);

        assertThatThrownBy(() -> service.confirmPrivatePublish(7L, 8L, actor))
                .isInstanceOf(DomainBadRequestException.class).hasMessage("error.suite.retired");
        assertThat(version.getStatus()).isEqualTo(SkillSuiteVersionStatus.DRAFT);
        assertThat(service.allowedActions(suite, version, new Namespace("team", "Team", "admin"), actor))
                .doesNotContain(SkillSuiteAllowedAction.EDIT, SkillSuiteAllowedAction.PUBLISH_PRIVATE,
                        SkillSuiteAllowedAction.DELETE);

        // Fixture represents the durable eligibility set by the cutover migration or legacy Bundle.
        version.allowRetirementCompletion();
        assertThat(service.confirmPrivatePublish(7L, 8L, actor).getStatus())
                .isEqualTo(SkillSuiteVersionStatus.PUBLISHED);
        assertThat(suite.getLatestVersionId()).isEqualTo(8L);
        assertThatThrownBy(() -> service.reopenRejected(7L, 8L, actor))
                .isInstanceOf(DomainBadRequestException.class).hasMessage("error.suite.retired");
    }

    @Test
    void rejectsNewSuiteEvenForSuperAdministrator() {
        SkillSuiteDraftService service = new SkillSuiteDraftService(
                mock(SkillSuiteRepository.class), mock(SkillSuiteVersionRepository.class),
                mock(SkillSuiteVersionMemberRepository.class), mock(NamespaceRepository.class),
                mock(SkillSuitePublicationValidator.class), mock(AuditLogService.class),
                new SkillSuiteRetirementPolicy(true));
        CreateSkillSuiteDraftCommand command = new CreateSkillSuiteDraftCommand(
                1L, "writers", "Writers", "Writing skills", "Use writer", "1.0.0",
                SkillVisibility.PUBLIC, null, 10L,
                List.of(new SkillSuiteMemberSelection(2L, 10L, "global", "writer", "1.0.0", "sha256:abc")));
        SkillSuiteActionContext actor = new SkillSuiteActionContext(
                "admin", Map.of(), Set.of("SUPER_ADMIN"), "retirement-test", null, null);

        assertThatThrownBy(() -> service.create(command, actor))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessage("error.suite.retired");
        assertThatThrownBy(() -> service.createVersion(7L, command, actor))
                .isInstanceOf(DomainBadRequestException.class).hasMessage("error.suite.retired");
        assertThatThrownBy(() -> service.updateDraft(7L, 8L, command, actor))
                .isInstanceOf(DomainBadRequestException.class).hasMessage("error.suite.retired");
    }

    private static void setId(Object target, Long id) throws Exception {
        var field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
