package com.iflytek.skillhub.domain.plugin;

import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.plugin.service.PluginActor;
import com.iflytek.skillhub.domain.plugin.service.PluginLifecycleService;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import com.iflytek.skillhub.domain.plugin.service.PluginReleaseWorkflow;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PluginLifecycleServiceTest {
    @Test
    void approvalCreatesPendingPublicationWithoutPublishingTheRelease() throws Exception {
        NamespaceRepository namespaces = mock(NamespaceRepository.class);
        PluginProjectRepository projects = mock(PluginProjectRepository.class);
        PluginReleaseRepository releases = mock(PluginReleaseRepository.class);
        PluginDistributionRepository distributions = mock(PluginDistributionRepository.class);
        PluginReviewDecisionRepository reviews = mock(PluginReviewDecisionRepository.class);
        PluginPublicationTaskRepository tasks = mock(PluginPublicationTaskRepository.class);
        PluginProject project = new PluginProject(7L, "sample", "Sample", "owner", PluginVisibility.PRIVATE);
        setId(project, 10L);
        PluginRelease release = new PluginRelease(10L, "1.0.0", "owner");
        setId(release, 20L);
        PluginDistribution archive = PluginDistribution.hostedArchive(20L, "claude-code", "plugins/sha.zip",
                "a".repeat(64), "sample", "1.0.0", "[]", "{}");
        when(namespaces.findById(7L)).thenReturn(Optional.of(new Namespace("team", "Team", "owner")));
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(projects.findByIdForUpdate(10L)).thenReturn(Optional.of(project));
        when(releases.findByIdForUpdate(20L)).thenReturn(Optional.of(release));
        when(releases.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(distributions.findByReleaseIdOrderByRuntimeKey(20L)).thenReturn(List.of(archive));
        when(reviews.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PluginReleaseWorkflow workflow = new PluginReleaseWorkflow(namespaces, projects, releases, distributions,
                reviews, tasks);
        workflow.submit(20L, new PluginActor("owner", Map.of(7L, NamespaceRole.MEMBER), Set.of()));
        PluginPublicationTask task = workflow.approve(20L, "Reviewed", null, null,
                new PluginActor("reviewer", Map.of(), Set.of("PLUGIN_ADMIN")), false);
        assertThat(task.getStatus()).isEqualTo("QUEUED");
        assertThat(task.getRequiredChannels()).isEqualTo("HOSTED");
        assertThat(release.getStatus()).isEqualTo(PluginReleaseStatus.PENDING_REVIEW);
        assertThat(project.getLatestReleaseId()).isNull();
        assertThatThrownBy(() -> workflow.submit(20L,
                new PluginActor("owner", Map.of(7L, NamespaceRole.MEMBER), Set.of())))
                .isInstanceOf(DomainBadRequestException.class);
    }

    private static void setId(Object entity, Long id) throws Exception {
        var field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Test
    void outsiderCannotCreateProjectInAnotherNamespace() {
        NamespaceRepository namespaces = mock(NamespaceRepository.class);
        when(namespaces.findById(7L)).thenReturn(Optional.of(new Namespace("team", "Team", "owner")));
        PluginLifecycleService service = new PluginLifecycleService(namespaces, mock(PluginProjectRepository.class));
        assertThatThrownBy(() -> service.createProject(7L, "plugin", "Plugin", "", PluginVisibility.PRIVATE,
                new PluginActor("outsider", Map.of(), Set.of())))
                .isInstanceOf(DomainForbiddenException.class);
    }
}
