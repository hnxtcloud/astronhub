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
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.storage.ObjectStorageService;

import jakarta.persistence.EntityManager;

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
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

@SpringBootTest(properties = "skillhub.plugin.hosted-worker-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OfflinePluginImportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entities;
    @MockBean ObjectStorageService storage;
    private final Map<String, byte[]> objects = new HashMap<>();
    private long namespaceId;

    @BeforeEach
    void fixture() throws Exception {
        for (String id : List.of("plugin-owner", "plugin-reviewer", "plugin-outsider")) {
            entities.persist(new UserAccount(id, id, id + "@example.test", null));
        }
        Namespace namespace = new Namespace("plugin-http", "Plugins", "plugin-owner");
        entities.persist(namespace);
        entities.flush();
        namespaceId = namespace.getId();
        entities.persist(new NamespaceMember(namespaceId, "plugin-owner", NamespaceRole.MEMBER));
        entities.flush();
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

    @Test
    void previewAndSelectedImportRetainOriginalPiTgzAndConflictWithoutOverwriting()
            throws Exception {
        byte[] source;
        try (var bytes = new ByteArrayOutputStream()) {
            try (var gzip = new java.util.zip.GZIPOutputStream(bytes);
                    var tar =
                            new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(
                                    gzip)) {
                tarFile(
                        tar,
                        "package/package.json",
                        "{\"name\":\"@team/pi-tools\",\"version\":\"1.0.0\",\"pi\":{\"extensions\":[\"./extensions/tool.ts\"]},\"dependencies\":{\"left-pad\":\"1.3.0\"},\"scripts\":{\"postinstall\":\"exit"
                            + " 91\"}}");
                tarFile(tar, "package/extensions/tool.ts", "throw new Error('never execute')");
            }
            source = bytes.toByteArray();
        }
        JsonNode scan =
                mapper.readTree(
                                mvc.perform(
                                                post(base()
                                                                + "/imports?namespaceId="
                                                                + namespaceId
                                                                + "&format=TGZ&repository=false")
                                                        .with(
                                                                authentication(
                                                                        auth(
                                                                                "plugin-owner",
                                                                                "USER")))
                                                        .with(csrf())
                                                        .contentType("application/octet-stream")
                                                        .content(source))
                                        .andExpect(status().isOk())
                                        .andExpect(
                                                jsonPath("$.data.candidates[0].status")
                                                        .value("NEW_PLUGIN"))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data");
        String selection =
                "{\"selections\":[{\"candidateId\":\"1\",\"slug\":\"pi-tools\",\"visibility\":\"PRIVATE\"}]}";
        JsonNode imported =
                call(
                        "/imports/" + scan.path("id").asText() + "/apply",
                        selection,
                        "plugin-owner",
                        "USER");
        assertThat(imported.path("results").get(0).path("status").asText()).isEqualTo("IMPORTED");
        long release = imported.path("results").get(0).path("releaseId").asLong();
        mvc.perform(
                        get(base() + "/releases/" + release + "/archive?runtime=pi")
                                .with(authentication(auth("plugin-owner", "USER"))))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        org.hamcrest.Matchers.containsString(".tgz")))
                .andExpect(content().bytes(source));
        assertThat(
                        call(
                                        "/imports/" + scan.path("id").asText() + "/apply",
                                        selection,
                                        "plugin-owner",
                                        "USER")
                                .path("results")
                                .get(0)
                                .path("status")
                                .asText())
                .isEqualTo("IDENTICAL");
    }

    @Test
    void repositoryPermissionAndSelectionIsolationProtectNamespaceAndOtherUsersPreviews()
            throws Exception {
        byte[] source = repositoryZip();
        mvc.perform(
                        post(base()
                                        + "/imports?namespaceId="
                                        + namespaceId
                                        + "&format=ZIP&repository=true")
                                .with(authentication(auth("plugin-owner", "USER")))
                                .with(csrf())
                                .contentType("application/octet-stream")
                                .content(source))
                .andExpect(status().isForbidden());
        JsonNode scan =
                mapper.readTree(
                                mvc.perform(
                                                post(base()
                                                                + "/imports?namespaceId="
                                                                + namespaceId
                                                                + "&format=ZIP&repository=true")
                                                        .with(
                                                                authentication(
                                                                        auth(
                                                                                "plugin-reviewer",
                                                                                "PLUGIN_ADMIN")))
                                                        .with(csrf())
                                                        .contentType("application/octet-stream")
                                                        .content(source))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data");
        assertThat(scan.path("candidates")).hasSize(3);
        mvc.perform(
                        get(base() + "/imports/" + scan.path("id").asText())
                                .with(authentication(auth("plugin-owner", "USER"))))
                .andExpect(status().isForbidden());
        JsonNode imported =
                call(
                        "/imports/" + scan.path("id").asText() + "/apply",
                        "{\"selections\":[{\"candidateId\":\"1\",\"projectId\":999999},{\"candidateId\":\"2\"},{\"candidateId\":\"3\"}]}",
                        "plugin-reviewer",
                        "PLUGIN_ADMIN");
        assertThat(imported.path("results").get(0).path("status").asText()).isEqualTo("FAILED");
        assertThat(imported.path("results").get(1).path("status").asText()).isEqualTo("IMPORTED");
        assertThat(imported.path("results").get(2).path("status").asText()).isEqualTo("INVALID");
    }

    @Test
    void administratorBudgetsAffectNewUploadsWhileExistingPreviewKeepsItsSnapshot()
            throws Exception {
        byte[] zip = singleZip("original");
        JsonNode scan =
                mapper.readTree(
                                mvc.perform(
                                                post(base()
                                                                + "/imports?namespaceId="
                                                                + namespaceId
                                                                + "&format=ZIP")
                                                        .with(
                                                                authentication(
                                                                        auth(
                                                                                "plugin-owner",
                                                                                "USER")))
                                                        .with(csrf())
                                                        .contentType("application/octet-stream")
                                                        .content(zip))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .path("data");
        String limits =
                "{\"packageBytes\":1,\"repositoryBytes\":1,\"expandedBytes\":1,\"files\":1,\"candidates\":1}";
        mvc.perform(
                        put(base() + "/import-settings")
                                .with(authentication(auth("plugin-owner", "USER")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(limits))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put(base() + "/import-settings")
                                .with(authentication(auth("plugin-reviewer", "PLUGIN_ADMIN")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(limits))
                .andExpect(status().isOk());
        mvc.perform(
                        post(base() + "/imports?namespaceId=" + namespaceId + "&format=ZIP")
                                .with(authentication(auth("plugin-owner", "USER")))
                                .with(csrf())
                                .contentType("application/octet-stream")
                                .content(zip))
                .andExpect(status().isBadRequest());
        assertThat(
                        call(
                                        "/imports/" + scan.path("id").asText() + "/apply",
                                        "{\"selections\":[{\"candidateId\":\"1\"}]}",
                                        "plugin-owner",
                                        "USER")
                                .path("results")
                                .get(0)
                                .path("status")
                                .asText())
                .isEqualTo("IMPORTED");
    }

    @Test
    void sameVersionDifferentNativeBytesCannotReplaceAnExistingDraft() throws Exception {
        long release = 0;
        for (String description : List.of("first", "changed")) {
            JsonNode scan =
                    mapper.readTree(
                                    mvc.perform(
                                                    post(base()
                                                                    + "/imports?namespaceId="
                                                                    + namespaceId
                                                                    + "&format=ZIP")
                                                            .with(
                                                                    authentication(
                                                                            auth(
                                                                                    "plugin-owner",
                                                                                    "USER")))
                                                            .with(csrf())
                                                            .contentType("application/octet-stream")
                                                            .content(singleZip(description)))
                                            .andExpect(status().isOk())
                                            .andReturn()
                                            .getResponse()
                                            .getContentAsString())
                            .path("data");
            JsonNode result =
                    call(
                                    "/imports/" + scan.path("id").asText() + "/apply",
                                    "{\"selections\":[{\"candidateId\":\"1\"}]}",
                                    "plugin-owner",
                                    "USER")
                            .path("results")
                            .get(0);
            assertThat(result.path("status").asText())
                    .isEqualTo(description.equals("first") ? "IMPORTED" : "CONFLICT");
            if (release == 0) release = result.path("releaseId").asLong();
            else assertThat(result.path("releaseId").asLong()).isEqualTo(release);
        }
        mvc.perform(
                        get(base() + "/releases/" + release + "/archive")
                                .with(authentication(auth("plugin-owner", "USER"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(singleZip("first")));
    }

    private static byte[] singleZip(String description) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zipFile(
                    zip,
                    ".claude-plugin/plugin.json",
                    "{\"name\":\"single\",\"version\":\"1.0.0\",\"description\":\""
                            + description
                            + "\"}");
        }
        return bytes.toByteArray();
    }

    private static byte[] repositoryZip() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zipFile(
                    zip,
                    "a/.claude-plugin/plugin.json",
                    "{\"name\":\"alpha\",\"version\":\"1.0.0\"}");
            zipFile(
                    zip,
                    "b/.codex-plugin/plugin.json",
                    "{\"name\":\"beta\",\"version\":\"2.0.0\"}");
            zipFile(zip, "c/.claude-plugin/plugin.json", "{invalid");
        }
        return bytes.toByteArray();
    }

    private static void zipFile(ZipOutputStream zip, String name, String value) throws IOException {
        var entry = new ZipEntry(name);
        entry.setTime(315532800000L);
        zip.putNextEntry(entry);
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void tarFile(
            org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tar,
            String path,
            String value)
            throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        var entry = new org.apache.commons.compress.archivers.tar.TarArchiveEntry(path);
        entry.setSize(data.length);
        tar.putArchiveEntry(entry);
        tar.write(data);
        tar.closeArchiveEntry();
    }

    private JsonNode call(String path, String body, String user, String role) throws Exception {
        String json =
                mvc.perform(
                                post(base() + path)
                                        .with(authentication(auth(user, role)))
                                        .with(csrf())
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return mapper.readTree(json).path("data");
    }

    private static String base() {
        return "/api/v1/plugin-management";
    }

    private static UsernamePasswordAuthenticationToken auth(String id, String role) {
        return new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal(id, id, id + "@example.test", "", "local", Set.of(role)),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
