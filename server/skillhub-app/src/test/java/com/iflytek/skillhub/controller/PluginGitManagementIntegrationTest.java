package com.iflytek.skillhub.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.config.PluginPublicationProperties;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.storage.ObjectStorageService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@SpringBootTest(
        properties = {
            "skillhub.plugin.hosted-worker-enabled=false",
            "skillhub.plugin.publication.credentials.git-fixture=git-secret-must-not-appear"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PluginGitManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entities;
    @Autowired PlatformTransactionManager manager;
    @Autowired PluginPublicationProperties properties;
    @MockBean ObjectStorageService storage;
    Path root, remote, other;
    String user, namespace;
    Long namespaceId;
    final Map<String, byte[]> objects = new HashMap<>();

    @BeforeEach
    void setup() throws Exception {
        root = Files.createTempDirectory("git-marketplace-test-");
        remote = root.resolve("remote.git");
        other = root.resolve("other.git");
        gitInit(remote);
        gitInit(other);
        properties.setAllowedGitRemotes(
                List.of(remote.toUri().toString(), other.toUri().toString()));
        user = "git-" + UUID.randomUUID().toString().substring(0, 8);
        namespace = user;
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        status -> {
                            entities.persist(
                                    new UserAccount(user, user, user + "@test.invalid", null));
                            var value = new Namespace(namespace, "Git Test", user);
                            entities.persist(value);
                            entities.flush();
                            namespaceId = value.getId();
                            entities.persist(
                                    new NamespaceMember(namespaceId, user, NamespaceRole.OWNER));
                        });
        doAnswer(
                        invocation -> {
                            objects.put(
                                    invocation.getArgument(0),
                                    ((InputStream) invocation.getArgument(1)).readAllBytes());
                            return null;
                        })
                .when(storage)
                .putObject(anyString(), any(InputStream.class), anyLong(), anyString());
        when(storage.getObject(anyString()))
                .thenAnswer(
                        invocation ->
                                new ByteArrayInputStream(objects.get(invocation.getArgument(0))));
        when(storage.exists(anyString()))
                .thenAnswer(invocation -> objects.containsKey(invocation.getArgument(0)));
    }

    @AfterEach
    void cleanup() throws Exception {
        // Each test owns this exact temporary root; Git objects may have their DOS read-only bit
        // set.
        Path resolved = root.toAbsolutePath().normalize();
        assertThat(resolved.getParent())
                .isEqualTo(
                        Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize());
        try (var paths = Files.walk(resolved)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.getFileAttributeView(
                                path,
                                java.nio.file.attribute.DosFileAttributeView.class,
                                LinkOption.NOFOLLOW_LINKS)
                        != null)
                    Files.setAttribute(path, "dos:readonly", false, LinkOption.NOFOLLOW_LINKS);
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void configuredDestinationsRequireSessionCsrfRoleAndUniqueNamespaceBinding() throws Exception {
        mvc.perform(get(base() + "/git/settings")).andExpect(status().isUnauthorized());
        mvc.perform(get(base() + "/git/settings").with(authentication(auth("USER"))))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post(base() + "/git/targets")
                                .with(authentication(auth("PLUGIN_ADMIN")))
                                .contentType("application/json")
                                .content(config(remote)))
                .andExpect(status().is4xxClientError());
        mvc.perform(
                        post(base() + "/git/targets")
                                .with(authentication(auth("PLUGIN_ADMIN")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        config(remote)
                                                .replace(
                                                        remote.toUri().toString(),
                                                        "ext::sh -c unsafe")))
                .andExpect(status().isBadRequest());
        JsonNode target = call("/git/targets", config(remote));
        assertThat(target.path("credentialRef").asText()).isEqualTo("git-fixture");
        mvc.perform(get(base() + "/git/settings").with(authentication(auth("PLUGIN_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString(
                                                        "git-secret-must-not-appear"))));
        long second =
                new TransactionTemplate(manager)
                        .execute(
                                status -> {
                                    var value = new Namespace(user + "-second", "Other", user);
                                    entities.persist(value);
                                    entities.flush();
                                    return value.getId();
                                });
        mvc.perform(
                        post(base() + "/git/targets")
                                .with(authentication(auth("PLUGIN_ADMIN")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        config(remote)
                                                .replace(
                                                        "\"namespaceId\":" + namespaceId,
                                                        "\"namespaceId\":" + second)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvedPublicMultiHostReleasePushesFrozenTargetAndRetainsDurableIntent()
            throws Exception {
        long config = call("/git/targets", config(remote)).path("id").asLong();
        var imported = importArchive(archive(false), null, "PUBLIC");
        long project = imported.path("projectId").asLong(),
                release = imported.path("releaseId").asLong();
        assertThat(importArchive(archive(true), project, "PUBLIC").path("releaseId").asLong())
                .isEqualTo(release);
        call("/releases/" + release + "/submit", "{}");
        call(
                "/releases/" + release + "/approve",
                "{\"notes\":\"Static review; runtime unverified\"}");
        call("/git/targets", config(other));
        mvc.perform(get("/api/v1/plugins/" + namespace + "/native-flow"))
                .andExpect(status().isNotFound());
        var published = call("/releases/" + release + "/publish-channels", "{}");
        assertThat(published.get(0).path("state").asText()).isEqualTo("SUCCEEDED");
        var receipt = mapper.readTree(published.get(0).path("receipt").asText());
        assertThat(receipt.path("remote").asText()).isEqualTo(remote.toUri().toString());
        assertThat(receipt.path("commit").asText()).matches("[a-f0-9]{40}");
        var intents = getData("/releases/" + release + "/git-intents");
        assertThat(intents.size()).isEqualTo(1);
        assertThat(intents.get(0).path("commit").asText())
                .isEqualTo(receipt.path("commit").asText());
        assertThat(published.get(0).path("snapshot").asText()).contains("\"configId\":" + config);
        mvc.perform(get("/api/v1/plugins/" + namespace + "/native-flow"))
                .andExpect(status().isOk());
        assertThat(show(remote, "main:.agents/plugins/marketplace.json"))
                .contains("astronhub-" + namespace);
        assertThat(show(remote, "main:.claude-plugin/marketplace.json")).contains("native-flow");
        assertThat(show(remote, "main:plugins/claude-code/native-flow/1.0.0/commands/hello.md"))
                .isEqualTo("---\ndescription: Greeting\n---\nHello\r\n");
        call("/releases/" + release + "/publish-channels", "{}");
        assertThat(getData("/releases/" + release + "/git-intents").size()).isEqualTo(1);
        var process = new ProcessBuilder("git", "--git-dir=" + other, "show-ref").start();
        assertThat(process.waitFor()).isEqualTo(1);
    }

    @Test
    void restrictedReleaseOnlyPublishesAuthenticatedHostedChannel() throws Exception {
        call("/git/targets", config(remote));
        long release = importArchive(archive(false), null, "PRIVATE").path("releaseId").asLong();
        call("/releases/" + release + "/submit", "{}");
        call("/releases/" + release + "/approve", "{\"notes\":\"Restricted static review\"}");
        assertThat(call("/releases/" + release + "/publish-channels", "{}").size()).isZero();
        assertThat(getData("/releases/" + release + "/git-intents").size()).isZero();
        var process = new ProcessBuilder("git", "--git-dir=" + remote, "show-ref").start();
        assertThat(process.waitFor()).isEqualTo(1);
    }

    @Test
    void rejectedGitControlFilesLeaveHostedSuccessAndExposeSanitizedChannelError()
            throws Exception {
        call("/git/targets", config(remote));
        byte[] archive;
        try (var bytes = new ByteArrayOutputStream()) {
            try (var zip = new ZipOutputStream(bytes)) {
                for (var file :
                        Map.of(
                                        ".claude-plugin/plugin.json",
                                        "{\"name\":\"native-flow\",\"version\":\"1.0.0\"}",
                                        ".git/hooks/pre-push",
                                        "exit 87")
                                .entrySet()) {
                    zip.putNextEntry(new ZipEntry(file.getKey()));
                    zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            archive = bytes.toByteArray();
        }
        long release = importArchive(archive, null, "PUBLIC").path("releaseId").asLong();
        call("/releases/" + release + "/submit", "{}");
        call("/releases/" + release + "/approve", "{\"notes\":\"Unverified fixture\"}");
        var channels = call("/releases/" + release + "/publish-channels", "{}");
        assertThat(channels.get(0).path("state").asText()).isEqualTo("FAILED");
        assertThat(channels.get(0).path("lastError").asText()).isEqualTo("GIT_METADATA_REJECTED");
        assertThat(getData("/releases/" + release + "/git-intents").size()).isZero();
        mvc.perform(get("/api/v1/plugins/" + namespace + "/native-flow"))
                .andExpect(status().isNotFound());
        var process = new ProcessBuilder("git", "--git-dir=" + remote, "show-ref").start();
        assertThat(process.waitFor()).isEqualTo(1);
    }

    @Test
    void publicMcpDetailReturns200And404WithoutAnOuterTransaction() throws Exception {
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        status -> {
                            var entry =
                                    new com.iflytek.skillhub.domain.mcp.McpServerEntry(
                                            namespaceId,
                                            "linked-mcp",
                                            "Linked MCP",
                                            user,
                                            com.iflytek.skillhub.domain.mcp.McpVisibility.PUBLIC);
                            entities.persist(entry);
                            entities.flush();
                            var revision =
                                    new com.iflytek.skillhub.domain.mcp.McpEntryRevision(
                                            entry.getId(), 1, "MANUAL", user);
                            org.springframework.test.util.ReflectionTestUtils.setField(
                                    revision,
                                    "status",
                                    com.iflytek.skillhub.domain.mcp.McpRevisionStatus.PUBLISHED);
                            entities.persist(revision);
                            entities.flush();
                            entry.pointToPublishedRevision(revision);
                        });
        mvc.perform(get("/api/v1/mcp-servers/" + namespace + "/linked-mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Linked MCP"));
        mvc.perform(get("/api/v1/mcp-servers/" + namespace + "/missing"))
                .andExpect(status().isNotFound());
    }

    JsonNode importArchive(byte[] bytes, Long project, String visibility) throws Exception {
        String preview =
                mvc.perform(
                                post(base() + "/imports?namespaceId=" + namespaceId + "&format=ZIP")
                                        .with(authentication(auth("PLUGIN_ADMIN")))
                                        .with(csrf())
                                        .contentType("application/octet-stream")
                                        .content(bytes))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String id = mapper.readTree(preview).path("data").path("id").asText();
        return call(
                        "/imports/" + id + "/apply",
                        "{\"selections\":[{\"candidateId\":\"1\","
                                + (project == null
                                        ? "\"slug\":\"native-flow\","
                                        : "\"projectId\":" + project + ",")
                                + "\"visibility\":\""
                                + visibility
                                + "\"}]}")
                .path("results")
                .get(0);
    }

    byte[] archive(boolean codex) throws Exception {
        try (var bytes = new ByteArrayOutputStream()) {
            try (var zip = new ZipOutputStream(bytes)) {
                var files =
                        codex
                                ? Map.of(
                                        "plugin.json",
                                        "{\"$schema\":\"https://agent-plugins.org/schemas/1.0.0/plugin.schema.json\",\"name\":\"native-flow\",\"version\":\"1.0.0\",\"description\":\"Fixture\"}",
                                        "skills/greeting/SKILL.md",
                                        "---\n"
                                                + "name: greeting\n"
                                                + "description: Greeting\n"
                                                + "---\n"
                                                + "Say hello.\n")
                                : Map.of(
                                        ".claude-plugin/plugin.json",
                                        "{\"name\":\"native-flow\",\"version\":\"1.0.0\",\"description\":\"Fixture\",\"commands\":[\"./commands/hello.md\"]}",
                                        "commands/hello.md",
                                        "---\ndescription: Greeting\n---\nHello\r\n");
                for (var file : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(file.getKey()));
                    zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        }
    }

    String config(Path target) throws Exception {
        return mapper.writeValueAsString(
                Map.of(
                        "namespaceId",
                        namespaceId,
                        "remote",
                        target.toUri().toString(),
                        "fetchUrl",
                        target.toUri().toString(),
                        "branch",
                        "main",
                        "credentialRef",
                        "git-fixture"));
    }

    JsonNode call(String path, String body) throws Exception {
        return mapper.readTree(
                        mvc.perform(
                                        post(base() + path)
                                                .with(authentication(auth("PLUGIN_ADMIN")))
                                                .with(csrf())
                                                .contentType("application/json")
                                                .content(body))
                                .andExpect(status().isOk())
                                .andExpect(
                                        content()
                                                .string(
                                                        org.hamcrest.Matchers.not(
                                                                org.hamcrest.Matchers
                                                                        .containsString(
                                                                                "git-secret-must-not-appear"))))
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("data");
    }

    JsonNode getData(String path) throws Exception {
        return mapper.readTree(
                        mvc.perform(get(base() + path).with(authentication(auth("PLUGIN_ADMIN"))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("data");
    }

    static void gitInit(Path path) throws Exception {
        assertThat(
                        new ProcessBuilder("git", "init", "--bare", path.toString())
                                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                                .redirectError(ProcessBuilder.Redirect.DISCARD)
                                .start()
                                .waitFor())
                .isZero();
    }

    static String show(Path repo, String path) throws Exception {
        var process = new ProcessBuilder("git", "--git-dir=" + repo, "show", path).start();
        String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).isZero();
        return value;
    }

    static String base() {
        return "/api/v1/plugin-management";
    }

    UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal(
                        user, user, user + "@test.invalid", "", "local", Set.of(role)),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
