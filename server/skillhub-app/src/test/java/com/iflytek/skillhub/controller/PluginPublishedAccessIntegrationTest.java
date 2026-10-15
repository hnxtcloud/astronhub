package com.iflytek.skillhub.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.mcp.*;
import com.iflytek.skillhub.domain.namespace.*;
import com.iflytek.skillhub.domain.plugin.*;
import com.iflytek.skillhub.domain.skill.*;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.storage.ObjectStorageService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.*;
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
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

@SpringBootTest(properties = "skillhub.plugin.hosted-worker-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PluginPublishedAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entities;
    @MockBean ObjectStorageService storage;
    Map<String, byte[]> objects = new HashMap<>();
    long namespaceId;
    PluginProject project;
    PluginRelease release;
    byte[] archive;

    @BeforeEach
    void fixture() throws Exception {
        // The preceding test's transaction rollback may clean up its newly uploaded objects
        // after Spring resets mocks. Only calls from this test are relevant to governance.
        clearInvocations(storage);
        for (String id : List.of("govern-owner", "govern-reader", "govern-admin"))
            entities.persist(new UserAccount(id, id, id + "@example.test", null));
        var namespace = new Namespace("govern-plugins", "Plugins", "govern-owner");
        entities.persist(namespace);
        entities.flush();
        namespaceId = namespace.getId();
        entities.persist(new NamespaceMember(namespaceId, "govern-owner", NamespaceRole.MEMBER));
        project =
                new PluginProject(
                        namespaceId, "sample", "Sample", "govern-owner", PluginVisibility.PUBLIC);
        entities.persist(project);
        entities.flush();
        release = new PluginRelease(project.getId(), "1.0.0", "govern-owner");
        release.submit();
        release.publish();
        entities.persist(release);
        entities.flush();
        project.pointToPublishedRelease(release);
        archive = zip();
        String key = "plugins/archives/fixture.zip";
        objects.put(key, archive);
        String digest =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
        entities.persist(
                PluginDistribution.hostedArchive(
                        release.getId(),
                        "claude-code",
                        key,
                        digest,
                        "sample",
                        "1.0.0",
                        "[\".claude-plugin/plugin.json\",\"README.md\",\"data.bin\"]",
                        "{\"name\":\"sample\",\"version\":\"1.0.0\"}"));
        entities.flush();
        doAnswer(
                        i -> {
                            objects.put(
                                    i.getArgument(0),
                                    ((InputStream) i.getArgument(1)).readAllBytes());
                            return null;
                        })
                .when(storage)
                .putObject(anyString(), any(InputStream.class), anyLong(), anyString());
        when(storage.getObject(anyString()))
                .thenAnswer(i -> new ByteArrayInputStream(objects.get(i.getArgument(0))));
        when(storage.exists(anyString())).thenAnswer(i -> objects.containsKey(i.getArgument(0)));
    }

    @Test
    void
            publishedFixedVersionHasSafeFileInspectionAuthenticatedExactDownloadAndAuditedInstructions()
                    throws Exception {
        mvc.perform(get(publicBase() + "/versions/1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.distributions[0].sha256").isNotEmpty());
        mvc.perform(
                        get(publicBase() + "/versions/1.0.0/download")
                                .with(authentication(auth("govern-reader", "USER"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(archive));
        mvc.perform(
                        get(publicBase() + "/versions/1.0.0/file")
                                .param("path", "README.md")
                                .with(authentication(auth("govern-reader", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("<script>alert('literal')</script>"));
        mvc.perform(
                        get(publicBase() + "/versions/1.0.0/file")
                                .param("path", "../README.md")
                                .with(authentication(auth("govern-reader", "USER"))))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get(publicBase() + "/versions/1.0.0/file")
                                .param("path", "data.bin")
                                .with(authentication(auth("govern-reader", "USER"))))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get(publicBase() + "/versions/1.0.0/install")
                                .with(authentication(auth("govern-reader", "USER"))))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.command")
                                .value(org.hamcrest.Matchers.containsString("--plugin-dir")));
        mvc.perform(
                        get("/api/v1/admin/audit-logs")
                                .param("action", "plugin.install.instructions")
                                .with(authentication(auth("govern-admin", "SUPER_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void lateOlderPublicationCannotRewindCurrentAndYankSelectsPreviousApprovedVersion()
            throws Exception {
        project.changeVisibility(PluginVisibility.PRIVATE);
        entities.flush();
        long older =
                managed(
                                "/projects/" + project.getId() + "/releases",
                                "{\"version\":\"1.1.0\"}",
                                "govern-owner",
                                "USER")
                        .path("id")
                        .asLong();
        long newer =
                managed(
                                "/projects/" + project.getId() + "/releases",
                                "{\"version\":\"1.2.0\"}",
                                "govern-owner",
                                "USER")
                        .path("id")
                        .asLong();
        for (var candidate :
                Map.of(older, "1.1.0", newer, "1.2.0").entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList()) {
            mvc.perform(
                            post("/api/v1/plugin-management/releases/"
                                            + candidate.getKey()
                                            + "/archive")
                                    .with(authentication(auth("govern-owner", "USER")))
                                    .with(csrf())
                                    .contentType("application/zip")
                                    .content(zip(candidate.getValue())))
                    .andExpect(status().isOk());
            managed("/releases/" + candidate.getKey() + "/submit", "{}", "govern-owner", "USER");
            managed(
                    "/releases/" + candidate.getKey() + "/approve",
                    "{\"notes\":\"Reviewed\"}",
                    "govern-admin",
                    "PLUGIN_ADMIN");
        }
        managed("/releases/" + newer + "/publish-hosted", "{}", "govern-admin", "PLUGIN_ADMIN");
        managed("/releases/" + older + "/publish-hosted", "{}", "govern-admin", "PLUGIN_ADMIN");
        mvc.perform(
                        get("/api/v1/plugin-management/projects/" + project.getId())
                                .with(authentication(auth("govern-owner", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestReleaseId").value(newer));
        managed(
                "/releases/" + newer + "/yank",
                "{\"reason\":\"Withdraw unsafe version\"}",
                "govern-owner",
                "USER");
        mvc.perform(
                        get("/api/v1/plugin-management/projects/" + project.getId())
                                .with(authentication(auth("govern-owner", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestReleaseId").value(older));
        mvc.perform(
                        get(publicBase() + "/versions/1.2.0")
                                .with(authentication(auth("govern-owner", "USER"))))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get(publicBase() + "/versions/1.1.0")
                                .with(authentication(auth("govern-owner", "USER"))))
                .andExpect(status().isOk());
    }

    @Test
    void governanceRevokesReadsRetainsBytesAndRequiresLiveMembership() throws Exception {
        mvc.perform(
                        post("/api/v1/plugin-management/projects/"
                                        + project.getId()
                                        + "/governance")
                                .with(authentication(auth("govern-reader", "USER")))
                                .with(csrf())
                                .contentType("application/json")
                                .content("{\"action\":\"HIDE\",\"reason\":\"No permission\"}"))
                .andExpect(status().isForbidden());
        for (String action : List.of("HIDE", "SHOW", "ARCHIVE", "RESTORE")) {
            managed(
                    "/projects/" + project.getId() + "/governance",
                    "{\"action\":\""
                            + action
                            + "\",\"reason\":\"Operator requested "
                            + action
                            + "\"}",
                    "govern-owner",
                    "USER");
            boolean visible = action.equals("SHOW") || action.equals("RESTORE");
            mvc.perform(get(publicBase() + "/versions/1.0.0"))
                    .andExpect(status().is(visible ? 200 : 404));
            mvc.perform(
                            get(publicBase() + "/versions/1.0.0/download")
                                    .with(authentication(auth("govern-reader", "USER"))))
                    .andExpect(status().is(visible ? 200 : 404));
        }
        managed(
                "/projects/" + project.getId() + "/governance",
                "{\"action\":\"VISIBILITY\",\"visibility\":\"PRIVATE\",\"reason\":\"Restrict"
                    + " catalog\"}",
                "govern-owner",
                "USER");
        mvc.perform(get(publicBase() + "/versions/1.0.0")).andExpect(status().isNotFound());
        mvc.perform(
                        get(publicBase() + "/versions/1.0.0")
                                .with(authentication(auth("govern-owner", "USER"))))
                .andExpect(status().isOk());
        entities.createQuery("delete from NamespaceMember m where m.namespaceId=:id")
                .setParameter("id", namespaceId)
                .executeUpdate();
        entities.flush();
        mvc.perform(
                        get(publicBase() + "/versions/1.0.0/download")
                                .with(authentication(auth("govern-owner", "USER"))))
                .andExpect(status().isNotFound());
        verify(storage, never()).deleteObject(anyString());
        assertThat(objects).hasSize(1);
    }

    @Test
    void linkedComponentsKeepIndependentVisibilityAndYankedLifecycle() throws Exception {
        var privateSkill = componentSkill("private-skill", SkillVisibility.PRIVATE);
        var publicSkill = componentSkill("public-skill", SkillVisibility.PUBLIC);
        managed(
                "/projects/" + project.getId() + "/components",
                "{\"kind\":\"SKILL\",\"namespace\":\"govern-plugins\",\"slug\":\"private-skill\",\"version\":\"1.0.0\"}",
                "govern-owner",
                "USER");
        managed(
                "/projects/" + project.getId() + "/components",
                "{\"kind\":\"SKILL\",\"namespace\":\"govern-plugins\",\"slug\":\"public-skill\",\"version\":\"1.0.0\"}",
                "govern-owner",
                "USER");
        mvc.perform(get(publicBase() + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components.length()").value(1))
                .andExpect(jsonPath("$.data.components[0].slug").value("public-skill"));
        mvc.perform(
                        get("/api/v1/plugin-management/projects/" + project.getId() + "/components")
                                .with(authentication(auth("govern-owner", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        publicSkill.setStatus(SkillVersionStatus.YANKED);
        entities.flush();
        mvc.perform(get(publicBase() + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components.length()").value(0));
        mvc.perform(
                        post("/api/v1/plugin-management/projects/"
                                        + project.getId()
                                        + "/components")
                                .with(authentication(auth("govern-admin", "PLUGIN_ADMIN")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"kind\":\"SKILL\",\"namespace\":\"govern-plugins\",\"slug\":\"private-skill\",\"version\":\"1.0.0\"}"))
                .andExpect(status().isForbidden());
        assertThat(privateSkill.getStatus()).isEqualTo(SkillVersionStatus.PUBLISHED);
    }

    private SkillVersion componentSkill(String slug, SkillVisibility visibility) {
        var skill = new Skill(namespaceId, slug, "govern-owner", visibility);
        entities.persist(skill);
        entities.flush();
        var v = new SkillVersion(skill.getId(), "1.0.0", "govern-owner");
        v.setStatus(SkillVersionStatus.PUBLISHED);
        v.setDownloadReady(true);
        v.setBundleReady(true);
        entities.persist(v);
        entities.flush();
        skill.setLatestVersionId(v.getId());
        return v;
    }

    @Test
    void mcpLinksNeverGrantPluginReviewerMcpAccessAndKeepFixedRevision() throws Exception {
        var entry =
                new McpServerEntry(
                        namespaceId,
                        "private-mcp",
                        "Secret MCP",
                        "govern-owner",
                        McpVisibility.PRIVATE);
        entities.persist(entry);
        entities.flush();
        var revision = new McpEntryRevision(entry.getId(), 1, "MANUAL", "govern-owner");
        org.springframework.test.util.ReflectionTestUtils.setField(
                revision, "status", McpRevisionStatus.PUBLISHED);
        entities.persist(revision);
        entities.flush();
        entry.pointToPublishedRevision(revision);
        var linked =
                managed(
                        "/projects/" + project.getId() + "/components",
                        "{\"kind\":\"MCP\",\"namespace\":\"govern-plugins\",\"slug\":\"private-mcp\",\"version\":\"1\"}",
                        "govern-owner",
                        "USER");
        long linkId = linked.get(0).path("id").asLong();
        mvc.perform(get(publicBase() + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components.length()").value(0));
        mvc.perform(
                        get("/api/v1/plugin-management/projects/" + project.getId() + "/components")
                                .with(authentication(auth("govern-admin", "PLUGIN_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(
                        delete(
                                        "/api/v1/plugin-management/projects/"
                                                + project.getId()
                                                + "/components/"
                                                + linkId)
                                .with(authentication(auth("govern-owner", "USER")))
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        assertThat(entities.find(McpEntryRevision.class, revision.getId()).getStatus())
                .isEqualTo(McpRevisionStatus.PUBLISHED);
        assertThat(entities.find(McpServerEntry.class, entry.getId()).getLatestRevisionId())
                .isEqualTo(revision.getId());
    }

    @Test
    void approvedPendingPublicationKeepsItsSnapshotWhenVisibilityChangeIsRejected()
            throws Exception {
        long id =
                managed(
                                "/projects/" + project.getId() + "/releases",
                                "{\"version\":\"1.1.0\"}",
                                "govern-owner",
                                "USER")
                        .path("id")
                        .asLong();
        mvc.perform(
                        post("/api/v1/plugin-management/releases/" + id + "/archive")
                                .with(authentication(auth("govern-owner", "USER")))
                                .with(csrf())
                                .contentType("application/zip")
                                .content(zip("1.1.0")))
                .andExpect(status().isOk());
        managed("/releases/" + id + "/submit", "{}", "govern-owner", "USER");
        managed(
                "/releases/" + id + "/approve",
                "{\"notes\":\"Reviewed\"}",
                "govern-admin",
                "PLUGIN_ADMIN");
        var task =
                entities.createQuery(
                                "from PluginPublicationTask t where t.releaseId=:id",
                                PluginPublicationTask.class)
                        .setParameter("id", id)
                        .getSingleResult();
        String snapshot = task.getContentSnapshot();
        mvc.perform(
                        post("/api/v1/plugin-management/projects/"
                                        + project.getId()
                                        + "/governance")
                                .with(authentication(auth("govern-owner", "USER")))
                                .with(csrf())
                                .contentType("application/json")
                                .content(
                                        "{\"action\":\"VISIBILITY\",\"visibility\":\"PRIVATE\",\"reason\":\"Must"
                                            + " not replan approval\"}"))
                .andExpect(status().isBadRequest());
        assertThat(task.getRequiredChannels()).isEqualTo("GIT,HOSTED");
        assertThat(task.getContentSnapshot()).isEqualTo(snapshot);
        assertThat(project.getVisibility()).isEqualTo(PluginVisibility.PUBLIC);
    }

    private JsonNode managed(String path, String body, String user, String role) throws Exception {
        return mapper.readTree(
                        mvc.perform(
                                        post("/api/v1/plugin-management" + path)
                                                .with(authentication(auth(user, role)))
                                                .with(csrf())
                                                .contentType("application/json")
                                                .content(body))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("data");
    }

    String publicBase() {
        return "/api/v1/plugins/govern-plugins/sample";
    }

    static UsernamePasswordAuthenticationToken auth(String id, String role) {
        return new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal(id, id, id + "@example.test", "", "local", Set.of(role)),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    static byte[] zip() throws IOException {
        return zip("1.0.0");
    }

    static byte[] zip(String version) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var value :
                    Map.of(
                                    ".claude-plugin/plugin.json",
                                    "{\"name\":\"sample\",\"version\":\"" + version + "\"}",
                                    "README.md",
                                    "<script>alert('literal')</script>",
                                    "data.bin",
                                    "\u0000binary")
                            .entrySet()) {
                zip.putNextEntry(new ZipEntry(value.getKey()));
                zip.write(value.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
