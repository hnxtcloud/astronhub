package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.skillhub.domain.plugin.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class PluginGitInstallationEvidenceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final PluginPublicationTaskRepository tasks = mock(PluginPublicationTaskRepository.class);
    final PluginPublicationChannelRepository channels =
            mock(PluginPublicationChannelRepository.class);
    final PluginProject project = mock(PluginProject.class);
    final PluginRelease release = mock(PluginRelease.class);
    final PluginDistribution archive = mock(PluginDistribution.class);
    final PluginPublicationChannel channel = mock(PluginPublicationChannel.class);
    final PluginInstallationQueryRepository query =
            new PluginInstallationQueryRepository(tasks, channels, mapper);
    ObjectNode receipt;

    @BeforeEach
    void setup() {
        when(project.getVisibility()).thenReturn(PluginVisibility.PUBLIC);
        when(release.getId()).thenReturn(7L);
        when(archive.getRuntimeKey()).thenReturn("codex");
        when(archive.getNativeName()).thenReturn("native-test");
        when(archive.getNativeVersion()).thenReturn("1.0.0");
        when(archive.getIntegrityDigest()).thenReturn("a".repeat(64));
        var task = mock(PluginPublicationTask.class);
        when(task.getId()).thenReturn(11L);
        when(tasks.findByReleaseId(7L)).thenReturn(Optional.of(task));
        when(channels.findByTaskIdOrderByChannel(11L)).thenReturn(List.of(channel));
        when(channel.getChannel()).thenReturn("GIT");
        when(channel.getState()).thenReturn("SUCCEEDED");
        receipt = mapper.createObjectNode();
        receipt.put("remote", "https://git.internal.example/team/plugins.git");
        receipt.put("commit", "b".repeat(40));
        receipt.put("marketplace", "astronhub-team");
        receipt.putArray("artifacts")
                .addObject()
                .put("runtime", "codex")
                .put("name", "native-test")
                .put("version", "1.0.0")
                .put("sha256", "a".repeat(64));
    }

    Optional<PluginInstallationQueryRepository.GitSource> read() throws Exception {
        when(channel.getReceipt()).thenReturn(mapper.writeValueAsString(receipt));
        return query.gitSource(project, release, archive);
    }

    @Test
    void confirmedReceiptSelectsFixedOriginalTarget() throws Exception {
        assertThat(read())
                .contains(
                        new PluginInstallationQueryRepository.GitSource(
                                "https://git.internal.example/team/plugins.git",
                                "b".repeat(40),
                                "astronhub-team"));
    }

    @Test
    void receiptMustMatchEveryNativeArtifactIdentityField() throws Exception {
        var artifact = (ObjectNode) receipt.path("artifacts").get(0);
        for (String key : List.of("runtime", "name", "version", "sha256")) {
            String original = artifact.path(key).asText();
            artifact.put(key, "other");
            assertThat(read()).as(key).isEmpty();
            artifact.put(key, original);
        }
        when(channel.getState()).thenReturn("FAILED");
        assertThat(read()).isEmpty();
    }

    @Test
    void restrictedOrUnsafeEvidenceCannotProduceNativeRecipe() throws Exception {
        for (String remote :
                List.of(
                        "https://user:secret@git.internal.example/r",
                        "https://git.internal.example/r?token=secret",
                        "ssh://git.internal.example/r",
                        "https://git.internal.example/r\ncommand")) {
            receipt.put("remote", remote);
            assertThat(read()).isEmpty();
        }
        receipt.put("remote", "https://git.internal.example/r");
        receipt.put("commit", "main");
        assertThat(read()).isEmpty();
        receipt.put("commit", "b".repeat(40));
        when(project.getVisibility()).thenReturn(PluginVisibility.PRIVATE);
        assertThat(read()).isEmpty();
    }
}
