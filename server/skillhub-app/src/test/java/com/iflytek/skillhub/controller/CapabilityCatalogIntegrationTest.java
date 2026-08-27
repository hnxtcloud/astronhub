package com.iflytek.skillhub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor;
import com.iflytek.skillhub.auth.oauth.GitLabClaimsExtractor;
import com.iflytek.skillhub.auth.oauth.OAuthLoginFlowService;
import com.iflytek.skillhub.bootstrap.BuiltinSkillRemotePackageDownloader;
import com.iflytek.skillhub.domain.mcp.McpEntryRevision;
import com.iflytek.skillhub.domain.mcp.McpServerEntry;
import com.iflytek.skillhub.domain.mcp.McpVisibility;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.plugin.PluginProject;
import com.iflytek.skillhub.domain.plugin.PluginRelease;
import com.iflytek.skillhub.domain.plugin.PluginVisibility;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.catalog.CapabilityCatalogItem;
import com.iflytek.skillhub.dto.catalog.CapabilityType;
import com.iflytek.skillhub.repository.CapabilityCatalogQueryRepository;
import com.iflytek.skillhub.repository.SkillCatalogQuerySource;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CapabilityCatalogIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapabilityCatalogQueryRepository catalogQueryRepository;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private GitHubClaimsExtractor gitHubClaimsExtractor;

    @MockBean
    private GitLabClaimsExtractor gitLabClaimsExtractor;

    @MockBean
    private OAuthLoginFlowService oAuthLoginFlowService;

    @MockBean
    private BuiltinSkillRemotePackageDownloader builtinSkillRemotePackageDownloader;

    @MockBean
    private SkillCatalogQuerySource skillCatalogQuerySource;

    @Test
    void shouldPublishCatalogEndpointsAndExecuteTypedQueries() throws Exception {
        assertThat(catalogQueryRepository.findPlugins("", 0, 20, null, Map.of(), Set.of()).items()).isEmpty();
        assertThat(catalogQueryRepository.findMcpServers("", 0, 20, null, Map.of(), Set.of()).items()).isEmpty();

        String contract = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(contract)
                .contains("/api/v1/plugins")
                .contains("/api/v1/mcp-servers")
                .contains("/api/v1/catalog/search")
                .contains("CapabilityCatalogItem");

        String output = System.getProperty("astronhub.openapi.output");
        if (output != null && !output.isBlank()) {
            Files.writeString(Path.of(output), contract);
        }
    }

    @Test
    void shouldApplyPluginVisibilityAndPublishedReleaseRules() {
        CatalogFixture fixture = createFixture("plugin-team");
        publishPlugin(fixture, "public-plugin", "owner", PluginVisibility.PUBLIC, false, false);
        publishPlugin(fixture, "team-plugin", "owner", PluginVisibility.NAMESPACE_ONLY, false, false);
        publishPlugin(fixture, "private-plugin", "owner", PluginVisibility.PRIVATE, false, false);
        publishPlugin(fixture, "hidden-plugin", "owner", PluginVisibility.PUBLIC, true, false);
        publishPlugin(fixture, "archived-plugin", "owner", PluginVisibility.PUBLIC, false, true);
        createDraftLatestPlugin(fixture, "draft-plugin", "owner");
        entityManager.flush();
        entityManager.clear();

        assertPluginSlugs("other", Map.of(), Set.of(), "public-plugin");
        assertPluginSlugs("member", Map.of(fixture.namespaceId(), NamespaceRole.MEMBER), Set.of(),
                "public-plugin", "team-plugin");
        assertPluginSlugs("owner", Map.of(), Set.of(),
                "private-plugin", "public-plugin", "team-plugin");
        assertPluginSlugs("admin", Map.of(fixture.namespaceId(), NamespaceRole.ADMIN), Set.of(),
                "private-plugin", "public-plugin", "team-plugin");
        assertPluginSlugs("other", Map.of(), Set.of("PLUGIN_ADMIN"),
                "private-plugin", "public-plugin", "team-plugin");
    }

    @Test
    void shouldApplyMcpVisibilityAndPublishedRevisionRules() {
        CatalogFixture fixture = createFixture("mcp-team");
        publishMcp(fixture, "public-mcp", "owner", McpVisibility.PUBLIC, false, false);
        publishMcp(fixture, "team-mcp", "owner", McpVisibility.NAMESPACE_ONLY, false, false);
        publishMcp(fixture, "private-mcp", "owner", McpVisibility.PRIVATE, false, false);
        publishMcp(fixture, "hidden-mcp", "owner", McpVisibility.PUBLIC, true, false);
        publishMcp(fixture, "archived-mcp", "owner", McpVisibility.PUBLIC, false, true);
        createDraftLatestMcp(fixture, "draft-mcp", "owner");
        entityManager.flush();
        entityManager.clear();

        assertMcpSlugs("other", Map.of(), Set.of(), "public-mcp");
        assertMcpSlugs("member", Map.of(fixture.namespaceId(), NamespaceRole.MEMBER), Set.of(),
                "public-mcp", "team-mcp");
        assertMcpSlugs("owner", Map.of(), Set.of(), "private-mcp", "public-mcp", "team-mcp");
        assertMcpSlugs("admin", Map.of(fixture.namespaceId(), NamespaceRole.OWNER), Set.of(),
                "private-mcp", "public-mcp", "team-mcp");
        assertMcpSlugs("other", Map.of(), Set.of("MCP_ADMIN"),
                "private-mcp", "public-mcp", "team-mcp");
    }

    @Test
    void shouldMergeAndPageAllCapabilityDomainsByUpdatedTime() {
        CatalogFixture fixture = createFixture("catalog-page-team");
        PluginProject pluginOlder = publishPlugin(
                fixture, "catalog-page-plugin-older", "owner", PluginVisibility.PUBLIC, false, false);
        McpServerEntry mcpOlder = publishMcp(
                fixture, "catalog-page-mcp-older", "owner", McpVisibility.PUBLIC, false, false);
        PluginProject pluginNewer = publishPlugin(
                fixture, "catalog-page-plugin-newer", "owner", PluginVisibility.PUBLIC, false, false);
        McpServerEntry mcpNewest = publishMcp(
                fixture, "catalog-page-mcp-newest", "owner", McpVisibility.PUBLIC, false, false);
        setPluginUpdatedAt(pluginOlder.getId(), "2026-01-01T00:00:00Z");
        setMcpUpdatedAt(mcpOlder.getId(), "2026-01-02T00:00:00Z");
        setPluginUpdatedAt(pluginNewer.getId(), "2026-01-03T00:00:00Z");
        setMcpUpdatedAt(mcpNewest.getId(), "2026-01-04T00:00:00Z");
        entityManager.flush();
        entityManager.clear();
        CapabilityCatalogItem skill = new CapabilityCatalogItem(
                CapabilityType.SKILL, 99L, "skill:@global/catalog-page-skill",
                "global", "catalog-page-skill", "Catalog Page Skill", "Skill summary",
                "PUBLIC", "ACTIVE", "1.0.0", List.of(), 5L,
                Instant.parse("2026-01-03T12:00:00Z"));
        given(skillCatalogQuerySource.findSkillsForCatalog(
                anyString(), anyInt(), anyInt(), anyString(), anyMap()))
                .willReturn(new PageResponse<>(List.of(skill), 1, 0, 6));

        var firstPage = catalogQueryRepository.findAll(
                "", 0, 3, "other", Map.of(), Set.of());
        var secondPage = catalogQueryRepository.findAll(
                "", 1, 3, "other", Map.of(), Set.of());

        assertThat(firstPage.total()).isEqualTo(5);
        assertThat(firstPage.items()).extracting(item -> item.slug()).containsExactly(
                "catalog-page-mcp-newest", "catalog-page-skill", "catalog-page-plugin-newer");
        assertThat(secondPage.total()).isEqualTo(5);
        assertThat(secondPage.items()).extracting(item -> item.slug()).containsExactly(
                "catalog-page-mcp-older", "catalog-page-plugin-older");
    }

    private CatalogFixture createFixture(String namespaceSlug) {
        for (String userId : Set.of("owner", "member", "admin", "other")) {
            entityManager.persist(new UserAccount(userId, userId, userId + "@example.test", null));
        }
        Namespace namespace = new Namespace(namespaceSlug, namespaceSlug, "owner");
        entityManager.persist(namespace);
        entityManager.flush();
        return new CatalogFixture(namespace.getId());
    }

    private PluginProject publishPlugin(CatalogFixture fixture, String slug, String ownerId,
                                        PluginVisibility visibility, boolean hidden, boolean archived) {
        PluginProject project = new PluginProject(fixture.namespaceId(), slug, slug, ownerId, visibility);
        entityManager.persist(project);
        entityManager.flush();
        PluginRelease release = new PluginRelease(project.getId(), "1.0.0", ownerId);
        entityManager.persist(release);
        entityManager.flush();
        entityManager.createNativeQuery("update plugin_release set status = 'PUBLISHED', published_at = current_timestamp where id = :id")
                .setParameter("id", release.getId())
                .executeUpdate();
        entityManager.clear();
        project = entityManager.find(PluginProject.class, project.getId());
        release = entityManager.find(PluginRelease.class, release.getId());
        project.pointToPublishedRelease(release);
        if (hidden) project.hide();
        if (archived) project.archive();
        return project;
    }

    private void createDraftLatestPlugin(CatalogFixture fixture, String slug, String ownerId) {
        PluginProject project = new PluginProject(
                fixture.namespaceId(), slug, slug, ownerId, PluginVisibility.PUBLIC);
        entityManager.persist(project);
        entityManager.flush();
        PluginRelease release = new PluginRelease(project.getId(), "0.1.0", ownerId);
        entityManager.persist(release);
        entityManager.flush();
        entityManager.createNativeQuery("update plugin_project set latest_release_id = :releaseId where id = :id")
                .setParameter("releaseId", release.getId())
                .setParameter("id", project.getId())
                .executeUpdate();
    }

    private McpServerEntry publishMcp(CatalogFixture fixture, String slug, String ownerId,
                                      McpVisibility visibility, boolean hidden, boolean archived) {
        McpServerEntry entry = new McpServerEntry(fixture.namespaceId(), slug, slug, ownerId, visibility);
        entityManager.persist(entry);
        entityManager.flush();
        McpEntryRevision revision = new McpEntryRevision(entry.getId(), 1, "MANUAL", ownerId);
        entityManager.persist(revision);
        entityManager.flush();
        entityManager.createNativeQuery("update mcp_entry_revision set status = 'PUBLISHED', published_at = current_timestamp where id = :id")
                .setParameter("id", revision.getId())
                .executeUpdate();
        entityManager.clear();
        entry = entityManager.find(McpServerEntry.class, entry.getId());
        revision = entityManager.find(McpEntryRevision.class, revision.getId());
        entry.pointToPublishedRevision(revision);
        if (hidden) entry.hide();
        if (archived) entry.archive();
        return entry;
    }

    private void createDraftLatestMcp(CatalogFixture fixture, String slug, String ownerId) {
        McpServerEntry entry = new McpServerEntry(
                fixture.namespaceId(), slug, slug, ownerId, McpVisibility.PUBLIC);
        entityManager.persist(entry);
        entityManager.flush();
        McpEntryRevision revision = new McpEntryRevision(entry.getId(), 1, "MANUAL", ownerId);
        entityManager.persist(revision);
        entityManager.flush();
        entityManager.createNativeQuery("update mcp_server_entry set latest_revision_id = :revisionId where id = :id")
                .setParameter("revisionId", revision.getId())
                .setParameter("id", entry.getId())
                .executeUpdate();
    }

    private void assertPluginSlugs(String userId, Map<Long, NamespaceRole> roles,
                                   Set<String> platformRoles, String... expected) {
        assertThat(catalogQueryRepository.findPlugins("", 0, 20, userId, roles, platformRoles).items())
                .extracting(item -> item.slug())
                .containsExactlyInAnyOrder(expected);
    }

    private void assertMcpSlugs(String userId, Map<Long, NamespaceRole> roles,
                                Set<String> platformRoles, String... expected) {
        assertThat(catalogQueryRepository.findMcpServers("", 0, 20, userId, roles, platformRoles).items())
                .extracting(item -> item.slug())
                .containsExactlyInAnyOrder(expected);
    }

    private void setPluginUpdatedAt(Long id, String timestamp) {
        entityManager.createNativeQuery("update plugin_project set updated_at = :timestamp where id = :id")
                .setParameter("timestamp", Timestamp.from(Instant.parse(timestamp)))
                .setParameter("id", id)
                .executeUpdate();
    }

    private void setMcpUpdatedAt(Long id, String timestamp) {
        entityManager.createNativeQuery("update mcp_server_entry set updated_at = :timestamp where id = :id")
                .setParameter("timestamp", Timestamp.from(Instant.parse(timestamp)))
                .setParameter("id", id)
                .executeUpdate();
    }

    private record CatalogFixture(Long namespaceId) {
    }
}
