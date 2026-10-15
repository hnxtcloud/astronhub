package com.iflytek.skillhub.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.user.UserAccount;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@SpringBootTest(
        properties = {
            "skillhub.plugin.hosted-worker-enabled=false",
            "skillhub.plugin.publication.allowed-registries[0]=http://127.0.0.1:19876/internal/",
            "skillhub.plugin.publication.credentials.fixture=secret-must-never-appear"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PluginNpmManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager entities;

    @BeforeEach
    void setup() {
        entities.persist(new UserAccount("npm-admin", "Npm Admin", "npm@test.invalid", null));
        entities.flush();
    }

    @Test
    void administratorConfiguresOnlyAllowedRegistryAndResponsesContainReferencesNotSecrets()
            throws Exception {
        var auth = auth("PLUGIN_ADMIN");
        String base = "/api/v1/plugin-management/npm";
        mvc.perform(get(base + "/settings")).andExpect(status().isUnauthorized());
        mvc.perform(
                        post(base + "/registries")
                                .with(authentication(auth))
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(
                        post(base + "/registries")
                                .with(authentication(auth("USER")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"name\":\"Internal\",\"registry\":\"http://127.0.0.1:19876/internal/\",\"credentialRef\":\"fixture\",\"defaultRegistry\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post(base + "/registries")
                                .with(authentication(auth))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"name\":\"Internal\",\"registry\":\"http://127.0.0.1:19876/internal/\",\"credentialRef\":\"fixture\",\"defaultRegistry\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialRef").value("fixture"))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "secret-must-never-appear"))));
    }

    @Autowired com.iflytek.skillhub.domain.plugin.PluginPublicationTaskRepository taskRepository;

    @Test
    void blockedPlansDoNotStarveTheTwentySecondExecutablePublication() {
        var namespace =
                new com.iflytek.skillhub.domain.namespace.Namespace(
                        "npm-fairness", "Npm", "npm-admin");
        entities.persist(namespace);
        entities.flush();
        var project =
                new com.iflytek.skillhub.domain.plugin.PluginProject(
                        namespace.getId(),
                        "fairness",
                        "Fairness",
                        "npm-admin",
                        com.iflytek.skillhub.domain.plugin.PluginVisibility.PUBLIC);
        entities.persist(project);
        entities.flush();
        Long executable = null;
        for (int i = 0; i < 22; i++) {
            var release =
                    new com.iflytek.skillhub.domain.plugin.PluginRelease(
                            project.getId(), "1.0." + i, "npm-admin");
            release.submit();
            entities.persist(release);
            entities.flush();
            var task =
                    new com.iflytek.skillhub.domain.plugin.PluginPublicationTask(
                            release.getId(), "npm-admin", "HOSTED,NPM", "PUBLIC");
            task.completeHosted();
            entities.persist(task);
            entities.flush();
            entities.persist(
                    new com.iflytek.skillhub.domain.plugin.PluginPublicationChannel(
                            task.getId(), "NPM", i < 21 ? null : "{fixed-plan}"));
            if (i == 21) executable = release.getId();
        }
        entities.flush();
        org.assertj.core.api.Assertions.assertThat(
                        taskRepository.findRunnable(
                                java.time.Instant.now(),
                                org.springframework.data.domain.PageRequest.of(0, 20)))
                .extracting(com.iflytek.skillhub.domain.plugin.PluginPublicationTask::getReleaseId)
                .containsExactly(executable);
    }

    @org.springframework.boot.test.mock.mockito.MockBean
    com.iflytek.skillhub.storage.ObjectStorageService storage;

    @org.springframework.boot.test.mock.mockito.MockBean
    com.iflytek.skillhub.service.plugin.NpmRegistryPublisher publisher;

    @Test
    void dependencyUploadIsIndependentConfirmedExplicitlyAndBoundToOriginalRegistry()
            throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var objects = new java.util.HashMap<String, byte[]>();
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            objects.put(
                                    invocation.getArgument(0),
                                    ((java.io.InputStream) invocation.getArgument(1))
                                            .readAllBytes());
                            return null;
                        })
                .when(storage)
                .putObject(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(java.io.InputStream.class),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.when(storage.getObject(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(
                        invocation ->
                                new java.io.ByteArrayInputStream(
                                        objects.get(invocation.getArgument(0))));
        long config =
                mapper.readTree(
                                mvc.perform(
                                                post("/api/v1/plugin-management/npm/registries")
                                                        .with(authentication(auth("PLUGIN_ADMIN")))
                                                        .with(csrf())
                                                        .contentType("application/json")
                                                        .content(
                                                                "{\"name\":\"Internal\",\"registry\":\"http://127.0.0.1:19876/internal/\",\"credentialRef\":\"fixture\",\"defaultRegistry\":true}"))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data")
                        .path("id")
                        .asLong();
        byte[] original;
        try (var bytes = new java.io.ByteArrayOutputStream()) {
            try (var gzip = new java.util.zip.GZIPOutputStream(bytes);
                    var tar =
                            new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(
                                    gzip)) {
                byte[] manifest =
                        "{\"name\":\"@team/dependency\",\"version\":\"1.2.3\",\"scripts\":{\"postinstall\":\"exit 87\"}}"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                var entry =
                        new org.apache.commons.compress.archivers.tar.TarArchiveEntry(
                                "package/package.json");
                entry.setSize(manifest.length);
                tar.putArchiveEntry(entry);
                tar.write(manifest);
                tar.closeArchiveEntry();
            }
            original = bytes.toByteArray();
        }
        var upload =
                mapper.readTree(
                                mvc.perform(
                                                post("/api/v1/plugin-management/npm/dependencies?registryId="
                                                                + config
                                                                + "&source=offline-test")
                                                        .with(authentication(auth("PLUGIN_ADMIN")))
                                                        .with(csrf())
                                                        .contentType("application/octet-stream")
                                                        .content(original))
                                        .andExpect(status().isOk())
                                        .andExpect(
                                                jsonPath("$.data.state")
                                                        .value("AWAITING_CONFIRMATION"))
                                        .andExpect(
                                                jsonPath("$.data.name").value("@team/dependency"))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data");
        org.mockito.Mockito.verifyNoInteractions(publisher);
        org.mockito.Mockito.when(
                        publisher.publish(
                                org.mockito.ArgumentMatchers.any(java.nio.file.Path.class),
                                org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(
                        invocation -> {
                            org.assertj.core.api.Assertions.assertThat(
                                            java.nio.file.Files.readAllBytes(
                                                    invocation.getArgument(0)))
                                    .isEqualTo(original);
                            var target =
                                    (com.iflytek.skillhub.service.plugin.NpmRegistryPublisher
                                                    .Target)
                                            invocation.getArgument(2);
                            org.assertj.core.api.Assertions.assertThat(target.configId())
                                    .isEqualTo(config);
                            return new com.iflytek.skillhub.service.plugin.NpmRegistryPublisher
                                    .Receipt(
                                    target.registry(),
                                    "@team/dependency",
                                    "1.2.3",
                                    upload.path("sha256").asText(),
                                    "sha512-fixture",
                                    false);
                        });
        // A new default revision does not change the already uploaded dependency's target snapshot.
        mvc.perform(
                        post("/api/v1/plugin-management/npm/registries")
                                .with(authentication(auth("PLUGIN_ADMIN")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"name\":\"New"
                                            + " revision\",\"registry\":\"http://127.0.0.1:19876/internal/\",\"credentialRef\":\"fixture\",\"defaultRegistry\":true}"))
                .andExpect(status().isOk());
        String confirm =
                "/api/v1/plugin-management/npm/dependencies/"
                        + upload.path("id").asLong()
                        + "/publish";
        mvc.perform(post(confirm).with(authentication(auth("PLUGIN_ADMIN"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("SUCCEEDED"));
        mvc.perform(post(confirm).with(authentication(auth("PLUGIN_ADMIN"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("SUCCEEDED"));
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(1))
                .publish(
                        org.mockito.ArgumentMatchers.any(java.nio.file.Path.class),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());
        mvc.perform(
                        get("/api/v1/plugin-management/projects")
                                .with(authentication(auth("PLUGIN_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void approvedPublicPiUsesFrozenRegistryPlanAndPublishesOnlyAfterNativeReceipt()
            throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var objects = new java.util.HashMap<String, byte[]>();
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            objects.put(
                                    invocation.getArgument(0),
                                    ((java.io.InputStream) invocation.getArgument(1))
                                            .readAllBytes());
                            return null;
                        })
                .when(storage)
                .putObject(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(java.io.InputStream.class),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.when(storage.getObject(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(
                        invocation ->
                                new java.io.ByteArrayInputStream(
                                        objects.get(invocation.getArgument(0))));
        org.mockito.Mockito.when(storage.exists(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> objects.containsKey(invocation.getArgument(0)));
        String base = "/api/v1/plugin-management";
        var namespace =
                new com.iflytek.skillhub.domain.namespace.Namespace("npm-flow", "Npm", "npm-admin");
        entities.persist(namespace);
        entities.flush();
        entities.persist(
                new com.iflytek.skillhub.domain.namespace.NamespaceMember(
                        namespace.getId(),
                        "npm-admin",
                        com.iflytek.skillhub.domain.namespace.NamespaceRole.MEMBER));
        entities.flush();
        long config =
                mapper.readTree(
                                mvc.perform(
                                                post(base + "/npm/registries")
                                                        .with(authentication(auth("PLUGIN_ADMIN")))
                                                        .with(csrf())
                                                        .contentType("application/json")
                                                        .content(
                                                                "{\"name\":\"Original\",\"registry\":\"http://127.0.0.1:19876/internal/\",\"credentialRef\":\"fixture\",\"defaultRegistry\":true}"))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data")
                        .path("id")
                        .asLong();
        byte[] original;
        try (var bytes = new java.io.ByteArrayOutputStream()) {
            try (var gzip = new java.util.zip.GZIPOutputStream(bytes);
                    var tar =
                            new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(
                                    gzip)) {
                byte[] manifest =
                        "{\"name\":\"@team/pi-flow\",\"version\":\"1.0.0\",\"keywords\":[\"pi-package\"],\"scripts\":{\"postinstall\":\"exit 87\"}}"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                var entry =
                        new org.apache.commons.compress.archivers.tar.TarArchiveEntry(
                                "package/package.json");
                entry.setSize(manifest.length);
                tar.putArchiveEntry(entry);
                tar.write(manifest);
                tar.closeArchiveEntry();
            }
            original = bytes.toByteArray();
        }
        var preview =
                mapper.readTree(
                                mvc.perform(
                                                post(base
                                                                + "/imports?namespaceId="
                                                                + namespace.getId()
                                                                + "&format=TGZ")
                                                        .with(authentication(auth("PLUGIN_ADMIN")))
                                                        .with(csrf())
                                                        .contentType("application/octet-stream")
                                                        .content(original))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data");
        var result =
                mapper.readTree(
                                mvc.perform(
                                                post(base
                                                                + "/imports/"
                                                                + preview.path("id").asText()
                                                                + "/apply")
                                                        .with(authentication(auth("PLUGIN_ADMIN")))
                                                        .with(csrf())
                                                        .contentType("application/json")
                                                        .content(
                                                                "{\"selections\":[{\"candidateId\":\"1\",\"slug\":\"pi-flow\",\"visibility\":\"PUBLIC\"}]}"))
                                        .andExpect(status().isOk())
                                        .andExpect(
                                                jsonPath("$.data.results[0].status")
                                                        .value("IMPORTED"))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data")
                        .path("results")
                        .get(0);
        long release = result.path("releaseId").asLong();
        mvc.perform(
                        post(base + "/releases/" + release + "/submit")
                                .with(authentication(auth("PLUGIN_ADMIN")))
                                .with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(
                        post(base + "/releases/" + release + "/approve")
                                .with(authentication(auth("PLUGIN_ADMIN")))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"notes\":\"Static review only\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));
        mvc.perform(get("/api/v1/plugins/npm-flow/pi-flow")).andExpect(status().isNotFound());
        mvc.perform(
                        post(base + "/npm/registries")
                                .with(authentication(auth("PLUGIN_ADMIN")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"name\":\"Later"
                                            + " default\",\"registry\":\"http://127.0.0.1:19876/internal/\",\"credentialRef\":\"fixture\",\"defaultRegistry\":true}"))
                .andExpect(status().isOk());
        org.mockito.Mockito.when(
                        publisher.publish(
                                org.mockito.ArgumentMatchers.any(java.nio.file.Path.class),
                                org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(
                        invocation -> {
                            org.assertj.core.api.Assertions.assertThat(
                                            java.nio.file.Files.readAllBytes(
                                                    invocation.getArgument(0)))
                                    .isEqualTo(original);
                            var target =
                                    (com.iflytek.skillhub.service.plugin.NpmRegistryPublisher
                                                    .Target)
                                            invocation.getArgument(2);
                            org.assertj.core.api.Assertions.assertThat(target.configId())
                                    .isEqualTo(config);
                            return new com.iflytek.skillhub.service.plugin.NpmRegistryPublisher
                                    .Receipt(
                                    target.registry(),
                                    "@team/pi-flow",
                                    "1.0.0",
                                    "fixture-digest",
                                    "sha512-fixture",
                                    false);
                        });
        mvc.perform(
                        post(base + "/releases/" + release + "/publish-channels")
                                .with(authentication(auth("PLUGIN_ADMIN")))
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].state").value("SUCCEEDED"))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "secret-must-never-appear"))));
        mvc.perform(get("/api/v1/plugins/npm-flow/pi-flow")).andExpect(status().isOk());
        mvc.perform(
                        post(base + "/releases/" + release + "/publish-channels")
                                .with(authentication(auth("PLUGIN_ADMIN")))
                                .with(csrf()))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(1))
                .publish(
                        org.mockito.ArgumentMatchers.any(java.nio.file.Path.class),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal(
                        "npm-admin", "Npm Admin", "npm@test.invalid", "", "local", Set.of(role)),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
