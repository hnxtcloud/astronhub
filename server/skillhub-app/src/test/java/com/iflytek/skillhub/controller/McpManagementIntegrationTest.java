package com.iflytek.skillhub.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.oauth.GitHubClaimsExtractor;
import com.iflytek.skillhub.auth.oauth.GitLabClaimsExtractor;
import com.iflytek.skillhub.auth.oauth.OAuthLoginFlowService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.bootstrap.BuiltinSkillRemotePackageDownloader;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;

import java.util.List;
import java.util.Set;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class McpManagementIntegrationTest {
    private static final String BASE = "/api/v1/mcp-management";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private jakarta.persistence.EntityManager entities;

    @MockBean
    private GitHubClaimsExtractor gitHubClaimsExtractor;
    @MockBean
    private GitLabClaimsExtractor gitLabClaimsExtractor;
    @MockBean
    private OAuthLoginFlowService oAuthLoginFlowService;
    @MockBean
    private BuiltinSkillRemotePackageDownloader builtinSkillRemotePackageDownloader;

    @Test
    void createRequiresNamespaceMembershipUniqueSlugsAndValidTemplates() throws Exception {
        fixture();

        mvc.perform(post(BASE + "/entries").with(authentication(auth("other"))).with(csrf())
                        .contentType("application/json").content(createBody("demo", validProfile())))
                .andExpect(status().isForbidden());

        mvc.perform(post(BASE + "/entries").with(authentication(auth("owner"))).with(csrf())
                        .contentType("application/json").content(createBody("demo", validProfile())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("demo"))
                .andExpect(jsonPath("$.data.canManage").value(true))
                .andExpect(jsonPath("$.data.revisions[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data.revisions[0].sourceKind").value("MANUAL"));

        mvc.perform(post(BASE + "/entries").with(authentication(auth("owner"))).with(csrf())
                        .contentType("application/json").content(createBody("demo", validProfile())))
                .andExpect(status().isConflict());

        mvc.perform(post(BASE + "/entries").with(authentication(auth("owner"))).with(csrf())
                        .contentType("application/json")
                        .content(createBody("broken", stdioWithoutCommand())))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/entries").with(authentication(auth("owner"))).with(csrf())
                        .contentType("application/json")
                        .content(createBody("leaky", secretWithDefault())))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/entries").with(authentication(auth("owner"))).with(csrf())
                        .contentType("application/json")
                        .content(createBody("bare-secret", secretWithoutPlaceholder())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewLifecyclePublishesOnlyThroughApprovalAndKeepsPublishedRevisionImmutable() throws Exception {
        fixture();
        JsonNode entry = createEntryAsOwner("knowledge");
        long revisionId = entry.at("/data/revisions/0/id").asLong();

        mvc.perform(post(BASE + "/revisions/" + revisionId + "/submit")
                        .with(authentication(auth("owner"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        mvc.perform(post(BASE + "/revisions/" + revisionId + "/approve")
                        .with(authentication(auth("owner"))).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(post(BASE + "/revisions/" + revisionId + "/approve")
                        .with(authentication(auth("ns-admin"))).with(csrf())
                        .contentType("application/json").content("{\"notes\":\"verified endpoint\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mvc.perform(get("/api/v1/mcp-servers/mcp-team/knowledge/connection-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profiles[0].endpointTemplate")
                        .value("https://example.test/mcp?key=${API_KEY}"));

        mvc.perform(put(BASE + "/revisions/" + revisionId).with(authentication(auth("owner"))).with(csrf())
                        .contentType("application/json").content(updateBody()))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(BASE + "/revisions/" + revisionId)
                        .with(authentication(auth("owner"))).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newDraftRevisionKeepsThePublishedTemplatesVisibleUntilApproval() throws Exception {
        fixture();
        JsonNode entry = createEntryAsOwner("rolling");
        long publishedRevision = entry.at("/data/revisions/0/id").asLong();
        mvc.perform(post(BASE + "/revisions/" + publishedRevision + "/submit")
                        .with(authentication(auth("owner"))).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/revisions/" + publishedRevision + "/approve")
                        .with(authentication(auth("ns-admin"))).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());

        JsonNode updated = mapper.readTree(mvc.perform(
                        post(BASE + "/entries/" + entry.at("/data/id").asLong() + "/revisions")
                                .with(authentication(auth("owner"))).with(csrf())
                                .contentType("application/json")
                                .content("""
                                        {"description":"bump endpoint","profiles":[{"profileKey":"remote",
                                        "displayName":"Remote","transportType":"STREAMABLE_HTTP",
                                        "endpointTemplate":"https://v2.example.test/mcp"}]}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long draftRevision = updated.at("/data/id").asLong();

        mvc.perform(get("/api/v1/mcp-servers/mcp-team/rolling/connection-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(1))
                .andExpect(jsonPath("$.data.profiles[0].endpointTemplate")
                        .value("https://example.test/mcp?key=${API_KEY}"));

        mvc.perform(post(BASE + "/revisions/" + draftRevision + "/submit")
                        .with(authentication(auth("owner"))).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/revisions/" + draftRevision + "/approve")
                        .with(authentication(auth("ns-admin"))).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/mcp-servers/mcp-team/rolling/connection-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(2))
                .andExpect(jsonPath("$.data.profiles[0].endpointTemplate").value("https://v2.example.test/mcp"));
    }

    @Test
    void serverJsonImportPrefillsTemplatesAndStoresTheSnapshot() throws Exception {
        fixture();
        // serverJson is transported as a raw string field; build the request via the mapper.
        var request = mapper.createObjectNode();
        request.put("namespaceId", namespaceId());
        request.put("slug", "imported");
        request.put("displayName", "Imported");
        request.put("visibility", "PUBLIC");
        request.put("serverJson", """
                {"name":"io.example/demo-server","description":"A demo MCP server",
                 "remotes":[{"type":"streamable-http","url":"https://mcp.example.test/api?key=${API_KEY}"}],
                 "packages":[{"registry_type":"npm","identifier":"@demo/server","version":"1.2.0",
                   "environment_variables":[{"name":"DEMO_TOKEN","description":"Access token","required":true}]}]}
                """.strip());

        JsonNode entry = mapper.readTree(mvc.perform(
                        post(BASE + "/entries").with(authentication(auth("owner"))).with(csrf())
                                .contentType("application/json").content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(entry.at("/data/revisions/0/sourceKind").asText()).isEqualTo("SERVER_JSON");
        assertThat(entry.at("/data/revisions/0/profiles")).hasSize(2);
        assertThat(entry.at("/data/revisions/0/profiles/0/profileKey").asText()).isEqualTo("package-1");
        assertThat(entry.at("/data/revisions/0/profiles/0/transportType").asText()).isEqualTo("STDIO");
        assertThat(entry.at("/data/revisions/0/profiles/0/commandTemplate").asText()).isEqualTo("npx");
        assertThat(entry.at("/data/revisions/0/profiles/0/arguments").toString()).contains("@demo/server@1.2.0");
        assertThat(entry.at("/data/revisions/0/profiles/0/variables/0/secret").asBoolean()).isTrue();
        assertThat(entry.at("/data/revisions/0/profiles/0/variables/0/defaultValue").isNull()).isTrue();
        assertThat(entry.at("/data/revisions/0/profiles/1/profileKey").asText()).isEqualTo("remote-1");
        assertThat(entry.at("/data/revisions/0/profiles/1/transportType").asText()).isEqualTo("STREAMABLE_HTTP");

        mvc.perform(post(BASE + "/entries").with(authentication(auth("owner"))).with(csrf())
                        .contentType("application/json")
                        .content(createBody("bad-import", validProfile()).replace(
                                "\"profiles\":", "\"serverJson\":\"{not json\",\"profiles\":")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pendingReviewInboxGovernanceAndDirectPublishFollowPermissions() throws Exception {
        fixture();
        JsonNode entry = createEntryAsOwner("governed");
        long entryId = entry.at("/data/id").asLong();
        long revisionId = entry.at("/data/revisions/0/id").asLong();
        mvc.perform(post(BASE + "/revisions/" + revisionId + "/submit")
                        .with(authentication(auth("owner"))).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get(BASE + "/entries?pendingReview=true").with(authentication(auth("ns-admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].slug").value("governed"));
        mvc.perform(get(BASE + "/entries?pendingReview=true").with(authentication(auth("other"))))
                .andExpect(status().isForbidden());

        mvc.perform(post(BASE + "/entries/" + entryId + "/governance")
                        .with(authentication(auth("ns-admin"))).with(csrf())
                        .contentType("application/json").content("{\"action\":\"HIDE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/entries/" + entryId + "/governance")
                        .with(authentication(auth("ns-admin"))).with(csrf())
                        .contentType("application/json")
                        .content("{\"action\":\"HIDE\",\"reason\":\"unreachable endpoint\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(true));
        mvc.perform(get("/api/v1/mcp-servers/mcp-team/governed"))
                .andExpect(status().isNotFound());

        mvc.perform(post(BASE + "/entries/" + entryId + "/governance")
                        .with(authentication(auth("ns-admin"))).with(csrf())
                        .contentType("application/json")
                        .content("{\"action\":\"SHOW\",\"reason\":\"fixed\"}"))
                .andExpect(status().isOk());

        mvc.perform(post(BASE + "/revisions/" + revisionId + "/direct-publish")
                        .with(authentication(auth("owner"))).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/revisions/" + revisionId + "/direct-publish")
                        .with(authentication(auth("root", "SUPER_ADMIN"))).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    // ---------------------------------------------------------------- helpers

    private void fixture() {
        for (String userId : Set.of("owner", "ns-admin", "other", "root")) {
            entities.persist(new UserAccount(userId, userId, userId + "@example.test", null));
        }
        Namespace namespace = new Namespace("mcp-team", "MCP Team", "owner");
        entities.persist(namespace);
        entities.flush();
        entities.persist(new NamespaceMember(namespace.getId(), "owner", NamespaceRole.MEMBER));
        entities.persist(new NamespaceMember(namespace.getId(), "ns-admin", NamespaceRole.ADMIN));
        entities.flush();
    }

    private long namespaceId() {
        return (long) entities.createQuery("select n.id from Namespace n where n.slug = 'mcp-team'")
                .getSingleResult();
    }

    private UsernamePasswordAuthenticationToken auth(String userId, String... platformRoles) {
        return new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal(userId, userId, userId + "@example.test", "", "local", Set.of(platformRoles)),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private JsonNode createEntryAsOwner(String slug) throws Exception {
        return mapper.readTree(mvc.perform(
                        post(BASE + "/entries").with(authentication(auth("owner"))).with(csrf())
                                .contentType("application/json").content(createBody(slug, validProfile())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String validProfile() {
        return """
                [{"profileKey":"remote","displayName":"Remote","transportType":"STREAMABLE_HTTP",
                  "endpointTemplate":"https://example.test/mcp?key=${API_KEY}",
                  "variables":[{"name":"API_KEY","required":true,"secret":true}]}]""";
    }

    private String stdioWithoutCommand() {
        return """
                [{"profileKey":"stdio","displayName":"Stdio","transportType":"STDIO"}]""";
    }

    private String secretWithDefault() {
        return """
                [{"profileKey":"remote","displayName":"Remote","transportType":"STREAMABLE_HTTP",
                  "endpointTemplate":"https://example.test/mcp?key=${API_KEY}",
                  "variables":[{"name":"API_KEY","required":true,"secret":true,"defaultValue":"leaky"}]}]""";
    }

    private String secretWithoutPlaceholder() {
        return """
                [{"profileKey":"remote","displayName":"Remote","transportType":"STREAMABLE_HTTP",
                  "endpointTemplate":"https://example.test/mcp",
                  "variables":[{"name":"API_KEY","required":true,"secret":true}]}]""";
    }

    private String createBody(String slug, String profiles) {
        return """
                {"namespaceId":%d,"slug":"%s","displayName":"%s","visibility":"PUBLIC","profiles":%s}"""
                .formatted(namespaceId(), slug, slug, profiles.strip());
    }

    private String updateBody() {
        return """
                {"description":"edit","profiles":[],"localCuration":null}""";
    }
}
