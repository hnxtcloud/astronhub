package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.storage.ObjectStorageService;
import jakarta.persistence.EntityManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "skillhub.plugin.hosted-worker-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PluginManagementIntegrationTest {
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
        entities.persist(namespace); entities.flush(); namespaceId = namespace.getId();
        entities.persist(new NamespaceMember(namespaceId, "plugin-owner", NamespaceRole.MEMBER));
        entities.flush();
        doAnswer(invocation -> {
            objects.put(invocation.getArgument(0), ((InputStream) invocation.getArgument(1)).readAllBytes());
            return null;
        }).when(storage).putObject(anyString(), any(InputStream.class), anyLong(), anyString());
        when(storage.getObject(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(objects.get(invocation.getArgument(0))));
        when(storage.exists(anyString())).thenAnswer(invocation -> objects.containsKey(invocation.getArgument(0)));
    }

    @Test
    void memberImportsRealArchiveAndReviewerPublishesRestrictedVersionThenHistoryDownloadsIdenticalBytes() throws Exception {
        String projectBody = mapper.writeValueAsString(Map.of("namespaceId", namespaceId, "slug", "sample",
                "displayName", "Sample", "summary", "Description", "visibility", "PRIVATE"));
        long projectId = call("/projects", projectBody, "plugin-owner", "USER").path("id").asLong();
        long releaseId = call("/projects/" + projectId + "/releases", "{\"version\":\"1.0.0\",\"changelog\":\"First\"}",
                "plugin-owner", "USER").path("id").asLong();
        mvc.perform(post(base() + "/releases/" + releaseId + "/submit").with(authentication(auth("plugin-owner", "USER")))
                .with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post(base() + "/releases/" + releaseId + "/direct-publish").with(authentication(auth("plugin-owner", "SUPER_ADMIN")))
                .with(csrf()).contentType("application/json").content("{\"notes\":\"Direct\"}"))
                .andExpect(status().isBadRequest());
        byte[] zip = archive();
        mvc.perform(post(base() + "/releases/" + releaseId + "/archive").with(authentication(auth("plugin-owner", "USER")))
                .with(csrf()).contentType("application/zip").content(zip)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.distributions[0].compatibilityEvidence").value("MANIFEST_VALIDATED"))
                .andExpect(jsonPath("$.data.distributions[0].securityAssessment").value("NOT_SCANNED"));
        call("/releases/" + releaseId + "/submit", "{}", "plugin-owner", "USER");
        mvc.perform(post(base() + "/releases/" + releaseId + "/approve").with(authentication(auth("plugin-owner", "USER")))
                .with(csrf()).contentType("application/json").content("{\"notes\":\"Self\"}"))
                .andExpect(status().isForbidden());
        JsonNode approved = call("/releases/" + releaseId + "/approve", "{\"notes\":\"Reviewed manifest\"}",
                "plugin-reviewer", "PLUGIN_ADMIN");
        assertThat(approved.path("publication").path("status").asText()).isEqualTo("QUEUED");
        assertThat(approved.path("status").asText()).isEqualTo("PENDING_REVIEW");
        mvc.perform(get("/api/v1/plugins/plugin-http/sample")).andExpect(status().isNotFound());
        JsonNode published = call("/releases/" + releaseId + "/publish-hosted", "{}", "plugin-reviewer", "PLUGIN_ADMIN");
        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        mvc.perform(get(base() + "/releases/" + releaseId + "/download")).andExpect(status().isUnauthorized());
        mvc.perform(get(base() + "/releases/" + releaseId + "/download")
                .with(authentication(auth("plugin-outsider", "USER")))).andExpect(status().isForbidden());
        byte[] downloaded = mvc.perform(get(base() + "/releases/" + releaseId + "/download")
                .with(authentication(auth("plugin-owner", "USER")))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(zip);
        mvc.perform(post(base() + "/releases/" + releaseId + "/archive").with(authentication(auth("plugin-owner", "USER")))
                .with(csrf()).contentType("application/zip").content(zip)).andExpect(status().isBadRequest());
        long next = call("/projects/" + projectId + "/releases", "{\"version\":\"1.1.0\"}", "plugin-owner", "USER").path("id").asLong();
        mvc.perform(post(base() + "/releases/" + next + "/archive").with(authentication(auth("plugin-owner", "USER")))
                .with(csrf()).contentType("application/zip").content(archive("1.1.0"))).andExpect(status().isOk());
        call("/releases/" + next + "/submit", "{}", "plugin-owner", "USER");
        call("/releases/" + next + "/approve", "{\"notes\":\"New version reviewed\"}", "plugin-reviewer", "PLUGIN_ADMIN");
        call("/releases/" + next + "/publish-hosted", "{}", "plugin-reviewer", "PLUGIN_ADMIN");
        mvc.perform(get(base() + "/projects/" + projectId).with(authentication(auth("plugin-owner", "USER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.latestReleaseId").value(next))
                .andExpect(jsonPath("$.data.releases.length()").value(2));
        mvc.perform(get(base() + "/releases/" + releaseId + "/download").with(authentication(auth("plugin-owner", "USER"))))
                .andExpect(status().isOk()).andExpect(content().bytes(zip));
    }

    @Test
    void reviewersCannotMaintainOtherOwnersAndFrozenNamespaceBlocksEvenSuperAdmin() throws Exception {
        long project = createProject("PRIVATE");
        mvc.perform(get(base() + "/projects/" + project).with(authentication(auth("plugin-outsider", "USER"))))
                .andExpect(status().isForbidden());
        mvc.perform(post(base() + "/projects/" + project + "/releases").with(authentication(auth("plugin-reviewer", "PLUGIN_ADMIN")))
                .with(csrf()).contentType("application/json").content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isForbidden());
        entities.find(Namespace.class, namespaceId).setStatus(NamespaceStatus.FROZEN); entities.flush();
        mvc.perform(post(base() + "/projects/" + project + "/releases").with(authentication(auth("plugin-owner", "SUPER_ADMIN")))
                .with(csrf()).contentType("application/json").content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isBadRequest());
        entities.find(Namespace.class, namespaceId).setStatus(NamespaceStatus.ARCHIVED); entities.flush();
        mvc.perform(patch(base() + "/projects/" + project).with(authentication(auth("plugin-owner", "SUPER_ADMIN")))
                .with(csrf()).contentType("application/json").content("{\"displayName\":\"Renamed\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicReleaseWaitsForGitAndApprovalCannotBeOverwritten() throws Exception {
        long project = createProject("PUBLIC");
        long release = importAndSubmit(project);
        call("/releases/" + release + "/approve", "{\"notes\":\"Approved\"}", "plugin-reviewer", "PLUGIN_ADMIN");
        JsonNode result = call("/releases/" + release + "/publish-hosted", "{}", "plugin-reviewer", "PLUGIN_ADMIN");
        assertThat(result.path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(result.path("publication").path("requiredChannels").asText()).isEqualTo("GIT,HOSTED");
        assertThat(result.path("publication").path("completedChannels").asText()).isEqualTo("HOSTED");
        assertThat(result.path("publication").path("status").asText()).isEqualTo("QUEUED");
        mvc.perform(get("/api/v1/plugins/plugin-http/sample")).andExpect(status().isNotFound());
        mvc.perform(post(base() + "/releases/" + release + "/approve").with(authentication(auth("plugin-reviewer", "PLUGIN_ADMIN")))
                .with(csrf()).contentType("application/json").content("{\"notes\":\"Again\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corruptedHostedContentIsNotPublishedAndRestoringOriginalBytesAllowsRetry() throws Exception {
        long release = importAndSubmit(createProject("PRIVATE"));
        call("/releases/" + release + "/approve", "{\"notes\":\"Approved\"}", "plugin-reviewer", "PLUGIN_ADMIN");
        String key = objects.keySet().iterator().next(); byte[] original = objects.get(key);
        objects.put(key, "corrupted".getBytes(StandardCharsets.UTF_8));
        JsonNode failed = call("/releases/" + release + "/publish-hosted", "{}", "plugin-reviewer", "PLUGIN_ADMIN");
        assertThat(failed.path("publication").path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("status").asText()).isEqualTo("PENDING_REVIEW");
        objects.put(key, original);
        assertThat(call("/releases/" + release + "/publish-hosted", "{}", "plugin-reviewer", "PLUGIN_ADMIN")
                .path("status").asText()).isEqualTo("PUBLISHED");
    }

    private long createProject(String visibility) throws Exception {
        return call("/projects", mapper.writeValueAsString(Map.of("namespaceId", namespaceId, "slug", "sample",
                "displayName", "Sample", "visibility", visibility)), "plugin-owner", "USER").path("id").asLong();
    }
    private long importAndSubmit(long project) throws Exception {
        long id = call("/projects/" + project + "/releases", "{\"version\":\"1.0.0\"}", "plugin-owner", "USER").path("id").asLong();
        mvc.perform(post(base() + "/releases/" + id + "/archive").with(authentication(auth("plugin-owner", "USER")))
                .with(csrf()).contentType("application/zip").content(archive())).andExpect(status().isOk());
        call("/releases/" + id + "/submit", "{}", "plugin-owner", "USER"); return id;
    }

    private JsonNode call(String path, String body, String user, String role) throws Exception {
        String json = mvc.perform(post(base() + path).with(authentication(auth(user, role))).with(csrf())
                        .contentType("application/json").content(body)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).path("data");
    }
    private static String base() { return "/api/v1/plugin-management"; }
    private static UsernamePasswordAuthenticationToken auth(String id, String role) {
        return new UsernamePasswordAuthenticationToken(new PlatformPrincipal(id, id, id + "@example.test", "", "local", Set.of(role)),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    private static byte[] archive() throws Exception { return archive("1.0.0"); }
    private static byte[] archive(String version) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(".claude-plugin/plugin.json"));
            zip.write(("{\"name\":\"sample\",\"version\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("commands/hello.md"));
            zip.write("# Hello".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
