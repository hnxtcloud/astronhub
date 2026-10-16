package com.iflytek.skillhub.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.user.UserAccount;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "skillhub.plugin.hosted-worker-enabled=false",
            "skillhub.plugin.publication.allowed-registries[0]=http://127.0.0.1:19876/internal/",
            "skillhub.plugin.publication.credentials.fixture=must-stay-secret"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PluginPublicationRecoveryIntegrationTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {50, 53})
    void attemptHistoryIsPagedWithoutLosingOlderDurableEvidence(int total) throws Exception {
        for (int i = 0; i < total; i++) {
            var now = java.time.Instant.now();
            var attempt =
                    new PluginChannelAttempt(
                            channel.getId(),
                            0,
                            java.util.UUID.randomUUID().toString(),
                            "PUBLISH",
                            now,
                            now.plusSeconds(300));
            attempt.finish("FAILED", null, "FIXTURE_NO_EFFECT", now);
            entities.persist(attempt);
        }
        entities.flush();
        String path =
                "/api/v1/plugin-management/releases/" + release.getId() + "/publication-attempts";
        mvc.perform(
                        get(path)
                                .param("channel", "NPM")
                                .param("page", "0")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "PLUGIN_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(50))
                .andExpect(jsonPath("$.data.total").value(total))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.hasMore").value(total > 50));
        mvc.perform(
                        get(path)
                                .param("channel", "NPM")
                                .param("page", "1")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "PLUGIN_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(total - 50))
                .andExpect(jsonPath("$.data.total").value(total))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.hasMore").value(false));
        mvc.perform(
                        get(path)
                                .param("channel", "NPM")
                                .param("page", "-1")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "PLUGIN_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManager entities;
    @Autowired ObjectMapper mapper;
    PluginProject project;
    PluginRelease release;
    PluginPublicationTask task;
    PluginPublicationChannel channel;
    PluginNpmRegistry target;

    @BeforeEach
    void fixture() {
        entities.persist(
                new UserAccount("recovery-admin", "Recovery", "recovery@test.invalid", null));
        var ns = new Namespace("recovery", "Recovery", "recovery-admin");
        entities.persist(ns);
        entities.flush();
        project =
                new PluginProject(
                        ns.getId(), "sample", "Sample", "recovery-admin", PluginVisibility.PUBLIC);
        entities.persist(project);
        entities.flush();
        release = new PluginRelease(project.getId(), "1.0.0", "recovery-admin");
        release.submit();
        entities.persist(release);
        entities.flush();
        entities.persist(
                PluginDistribution.hostedArchive(
                        release.getId(),
                        "pi",
                        "plugins/test.tgz",
                        "a".repeat(64),
                        "sample",
                        "1.0.0",
                        "{\"packaging\":\"ORIGINAL_NPM_TGZ\",\"files\":[]}",
                        "{\"name\":\"sample\",\"version\":\"1.0.0\"}"));
        task =
                new PluginPublicationTask(
                        release.getId(),
                        "recovery-admin",
                        "HOSTED,NPM",
                        "PUBLIC\n1.0.0\npi:" + "a".repeat(64));
        task.completeHosted();
        entities.persist(task);
        entities.flush();
        channel = new PluginPublicationChannel(task.getId(), "NPM", null);
        entities.persist(channel);
        target =
                new PluginNpmRegistry(
                        "Replacement",
                        "http://127.0.0.1:19876/internal/",
                        "fixture",
                        true,
                        "recovery-admin");
        entities.persist(target);
        entities.flush();
    }

    @Test
    void missingConfigurationRequiresExplicitPreviewedAuditedRevisionAndKeepsOriginalApproval()
            throws Exception {
        String path = "/api/v1/plugin-management/releases/" + release.getId();
        mvc.perform(
                        get(path + "/publication-channels")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "PLUGIN_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].state").value("BLOCKED"))
                .andExpect(jsonPath("$.data[0].snapshot").value("MISSING_CONFIGURATION"));
        String request =
                "{\"channel\":\"NPM\",\"targetRevisionId\":"
                        + target.getId()
                        + ",\"expectedRevision\":0,\"reason\":\"Configure approved missing"
                        + " target\"}";
        JsonNode preview = postJson(path + "/publication-replans/preview", request, "PLUGIN_ADMIN");
        String apply =
                request.substring(0, request.length() - 1)
                        + ",\"previewDigest\":\""
                        + preview.path("digest").asText()
                        + "\"}";
        mvc.perform(
                        post(path + "/publication-replans")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "USER")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(apply))
                .andExpect(status().isForbidden());
        postJson(path + "/publication-replans", apply, "PLUGIN_ADMIN");
        mvc.perform(
                        get(path + "/publication-recovery")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "PLUGIN_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].originalSnapshot").value("MISSING_CONFIGURATION"))
                .andExpect(jsonPath("$.data[0].planRevision").value(1))
                .andExpect(jsonPath("$.data[0].plans.length()").value(2))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "must-stay-secret"))));
        mvc.perform(
                        post(path + "/publication-replans")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "PLUGIN_ADMIN")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(apply))
                .andExpect(status().isBadRequest());
        assertThat(task.getContentSnapshot()).isEqualTo("PUBLIC\n1.0.0\npi:" + "a".repeat(64));
        assertThat(task.getRequiredChannels()).isEqualTo("HOSTED,NPM");
        mvc.perform(
                        get("/api/v1/admin/audit-logs")
                                .param("action", "plugin.publication.replan")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "SUPER_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    JsonNode postJson(String path, String body, String role) throws Exception {
        return mapper.readTree(
                        mvc.perform(
                                        post(path)
                                                .with(
                                                        authentication(
                                                                PluginPublishedAccessIntegrationTest
                                                                        .auth(
                                                                                "recovery-admin",
                                                                                role)))
                                                .with(csrf())
                                                .contentType("application/json")
                                                .content(body))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("data");
    }

    @org.springframework.boot.test.mock.mockito.MockBean
    com.iflytek.skillhub.storage.ObjectStorageService storage;

    @org.springframework.boot.test.mock.mockito.MockBean
    com.iflytek.skillhub.service.plugin.NpmRegistryPublisher publisher;

    @Test
    void selectedReplacementExecutesOnceAndDurableSuccessIsReconciledWithoutRepublishing()
            throws Exception {
        byte[] bytes = "approved native bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest =
                java.util.HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var distribution =
                entities.createQuery(
                                "from PluginDistribution d where d.releaseId=:id",
                                PluginDistribution.class)
                        .setParameter("id", release.getId())
                        .getSingleResult();
        org.springframework.test.util.ReflectionTestUtils.setField(
                distribution, "integrityDigest", digest);
        entities.flush();
        org.mockito.Mockito.when(storage.getObject(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(i -> new java.io.ByteArrayInputStream(bytes));
        org.mockito.Mockito.when(
                        publisher.publish(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(
                        new com.iflytek.skillhub.service.plugin.NpmRegistryPublisher.Receipt(
                                target.getRegistry(),
                                "sample",
                                "1.0.0",
                                digest,
                                "sha512-proof",
                                false));
        String path = "/api/v1/plugin-management/releases/" + release.getId();
        String request =
                "{\"channel\":\"NPM\",\"targetRevisionId\":"
                        + target.getId()
                        + ",\"expectedRevision\":0,\"reason\":\"Fix missing target\"}";
        var preview = postJson(path + "/publication-replans/preview", request, "PLUGIN_ADMIN");
        postJson(
                path + "/publication-replans",
                request.substring(0, request.length() - 1)
                        + ",\"previewDigest\":\""
                        + preview.path("digest").asText()
                        + "\"}",
                "PLUGIN_ADMIN");
        postJson(path + "/publish-channels", "{}", "PLUGIN_ADMIN");
        mvc.perform(
                        get(path + "/publication-recovery")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "PLUGIN_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data[0].attempts[0].outcome").value("SUCCEEDED"));
        postJson(path + "/publish-channels", "{}", "PLUGIN_ADMIN");
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(1))
                .publish(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.argThat(
                                t -> t.registry().equals(target.getRegistry())),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void unknownRemoteResultBlocksReplacementUntilReadOnlyOriginalTargetReconciliation()
            throws Exception {
        byte[] bytes = "approved native bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest =
                java.util.HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var distribution =
                entities.createQuery(
                                "from PluginDistribution d where d.releaseId=:id",
                                PluginDistribution.class)
                        .setParameter("id", release.getId())
                        .getSingleResult();
        org.springframework.test.util.ReflectionTestUtils.setField(
                distribution, "integrityDigest", digest);
        entities.flush();
        org.mockito.Mockito.when(storage.getObject(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(i -> new java.io.ByteArrayInputStream(bytes));
        org.mockito.Mockito.when(
                        publisher.publish(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(
                        new com.iflytek.skillhub.service.plugin.NpmRegistryPublisher.Failure(
                                "REGISTRY_WRITE_UNCERTAIN"));
        org.mockito.Mockito.when(
                        publisher.reconcile(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.empty());
        String path = "/api/v1/plugin-management/releases/" + release.getId();
        String request =
                "{\"channel\":\"NPM\",\"targetRevisionId\":"
                        + target.getId()
                        + ",\"expectedRevision\":0,\"reason\":\"Repair target\"}";
        var preview = postJson(path + "/publication-replans/preview", request, "PLUGIN_ADMIN");
        postJson(
                path + "/publication-replans",
                request.substring(0, request.length() - 1)
                        + ",\"previewDigest\":\""
                        + preview.path("digest").asText()
                        + "\"}",
                "PLUGIN_ADMIN");
        postJson(path + "/publish-channels", "{}", "PLUGIN_ADMIN");
        mvc.perform(
                        get(path + "/publication-recovery")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "PLUGIN_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].state").value("UNKNOWN"));
        mvc.perform(
                        post(path + "/publication-replans/preview")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "PLUGIN_ADMIN")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        request.replace(
                                                "\"expectedRevision\":0",
                                                "\"expectedRevision\":1")))
                .andExpect(status().isBadRequest());
        postJson(path + "/publish-channels", "{}", "PLUGIN_ADMIN");
        mvc.perform(
                        get(path + "/publication-recovery")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "PLUGIN_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].state").value("FAILED"))
                .andExpect(jsonPath("$.data[0].attempts[0].action").value("RECONCILE"));
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(1))
                .publish(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(1))
                .reconcile(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.argThat(
                                t -> t.registry().equals(target.getRegistry())),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void npmExplicitGovernanceReportsUnsupportedRemoteDeletionAndRetainsPublishedEvidence()
            throws Exception {
        channel.replan(
                0,
                "{\"target\":{\"configId\":"
                        + target.getId()
                        + ",\"registry\":\""
                        + target.getRegistry()
                        + "\",\"credentialRef\":\"fixture\"}}");
        String token = channel.claim(java.time.Instant.now());
        channel.finish(token, "retained-original-npm-receipt", null);
        task.completeChannel("NPM");
        release.publish();
        entities.flush();
        String path = "/api/v1/plugin-management/releases/" + release.getId();
        postJson(path + "/yank", "{\"reason\":\"Ordinary catalog withdrawal\"}", "PLUGIN_ADMIN");
        assertThat(channel.isSourceDisabled()).isFalse();
        String request =
                "{\"channel\":\"NPM\",\"action\":\"WITHDRAW_CURRENT\",\"expectedRevision\":1,\"reason\":\"Stop"
                    + " native source advertisement\"}";
        var preview = postJson(path + "/channel-governance/preview", request, "PLUGIN_ADMIN");
        postJson(
                path + "/channel-governance",
                request.substring(0, request.length() - 1)
                        + ",\"previewDigest\":\""
                        + preview.path("digest").asText()
                        + "\"}",
                "PLUGIN_ADMIN");
        mvc.perform(
                        get(path + "/publication-recovery")
                                .with(
                                        authentication(
                                                PluginPublishedAccessIntegrationTest.auth(
                                                        "recovery-admin", "PLUGIN_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data[0].sourceDisabled").value(true))
                .andExpect(jsonPath("$.data[0].receipt").value("retained-original-npm-receipt"))
                .andExpect(jsonPath("$.data[0].operations[0].state").value("UNSUPPORTED"))
                .andExpect(
                        jsonPath("$.data[0].operations[0].receipt")
                                .value(
                                        org.hamcrest.Matchers.containsString(
                                                "REMOTE_PACKAGE_RETAINED")));
        org.mockito.Mockito.verifyNoInteractions(publisher);
    }
}
