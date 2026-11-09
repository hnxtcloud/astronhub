package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class PublicationTaskIntegrationTest {
    @Autowired private com.iflytek.skillhub.config.SkillPublishProperties materialLimits;

    @Test
    void multipartAndResumableMaterialRespectTheSameConfiguredLimit() throws Exception {
        long previous = materialLimits.getMaxPackageSize();
        try {
            materialLimits.setMaxPackageSize(32);
            var auth = actor("publication-size-owner");
            var task = read(mvc.perform(post("/api/web/publications").with(authentication(auth)).with(csrf())));
            String path = "/api/web/publications/" + task.path("id").asText();
            mvc.perform(multipart(path + "/material?revision=0")
                    .file(new org.springframework.mock.web.MockMultipartFile("file", "sample.zip", "application/zip", new byte[33]))
                    .with(authentication(auth)).with(csrf())).andExpect(status().isBadRequest());
            mvc.perform(post(path + "/material-upload?revision=0").contentType("application/json")
                    .content("{\"fileName\":\"sample.zip\",\"byteSize\":33,\"replace\":false}")
                    .with(authentication(auth)).with(csrf())).andExpect(status().isBadRequest());
        } finally { materialLimits.setMaxPackageSize(previous); }
    }
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private com.iflytek.skillhub.domain.namespace.NamespaceRepository namespaces;
    @Autowired private com.iflytek.skillhub.domain.user.UserAccountRepository users;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired private com.iflytek.skillhub.domain.software.SoftwarePolicyStateRepository policyState;
    @Autowired private com.iflytek.skillhub.domain.software.SoftwarePolicyRevisionRepository policies;
    @Autowired private com.iflytek.skillhub.domain.software.SoftwareStorageAccountRepository accounts;
    @Autowired private com.iflytek.skillhub.domain.software.SoftwareApplicationRepository applications;
    @Autowired private com.iflytek.skillhub.auth.repository.UserRoleBindingRepository bindings;
    @Autowired private com.iflytek.skillhub.auth.repository.RoleRepository roles;

    @Test
    void draftSurvivesAnotherRequestAndIsPrivateToItsPublisher() throws Exception {
        String response = mvc.perform(post("/api/web/publications")
                        .with(authentication(actor("publication-owner"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(response).path("data").path("id").asText();
        mvc.perform(get("/api/web/publications/" + id).with(authentication(actor("publication-owner"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(get("/api/web/publications/" + id).with(authentication(actor("another-publisher"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/web/publications").with(authentication(actor("another-publisher"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }

    private UsernamePasswordAuthenticationToken actor(String id) {
        return new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal(id, id, "", "", "local", Set.of()), null, List.of());
    }

    @Test
    void staleSaveCannotOverwriteDraftAndCancellationKeepsItsRecord() throws Exception {
        String response = mvc.perform(post("/api/web/publications")
                        .with(authentication(actor("draft-editor"))).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(response).path("data").path("id").asText();
        String update = "{\"revision\":0,\"draft\":{\"name\":\"My application\",\"type\":\"SOFTWARE\"}}";
        mvc.perform(put("/api/web/publications/" + id).contentType("application/json").content(update)
                        .with(authentication(actor("draft-editor"))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(1));
        mvc.perform(put("/api/web/publications/" + id).contentType("application/json").content(update)
                        .with(authentication(actor("draft-editor"))).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/web/publications/" + id + "/cancel?revision=1")
                        .with(authentication(actor("draft-editor"))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(get("/api/web/publications/" + id).with(authentication(actor("draft-editor"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.draft.name").value("My application"));
    }

    @Test
    void uploadedSkillIsIdentifiedAndSubmittedOnceThroughItsOwnedTask() throws Exception {
        String owner = "publication-skill-owner";
        users.save(new com.iflytek.skillhub.domain.user.UserAccount(owner, owner, "pub@example.test", null));
        grant(owner, "SUPER_ADMIN");
        namespaces.save(new com.iflytek.skillhub.domain.namespace.Namespace("publication-skills", "Publications", owner));
        var auth = new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal(owner, owner, "", "", "local", Set.of("SUPER_ADMIN")), null, List.of());
        var created = mapper.readTree(mvc.perform(post("/api/web/publications")
                        .with(authentication(auth)).with(csrf())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
        String id = created.path("id").asText();
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("SKILL.md"));
            zip.write("---\nname: unified-example\ndescription: Task integration example\nversion: 1.0.0\n---\nExample instructions.\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        String uploaded = mvc.perform(multipart("/api/web/publications/" + id + "/material?revision=0")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "example.zip", "application/zip", bytes.toByteArray()))
                        .with(authentication(auth)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.draft.type").value("SKILL"))
                .andExpect(jsonPath("$.data.draft.name").value("unified-example"))
                .andReturn().getResponse().getContentAsString();
        var task = mapper.readTree(uploaded).path("data");
        var draft = (com.fasterxml.jackson.databind.node.ObjectNode) task.path("draft");
        draft.put("namespace", "publication-skills").put("visibility", "PRIVATE").put("confirmWarnings", true);
        String saved = mvc.perform(put("/api/web/publications/" + id).contentType("application/json")
                        .content(mapper.writeValueAsString(java.util.Map.of("revision", task.path("revision").asLong(), "draft", draft)))
                        .with(authentication(auth)).with(csrf())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long revision = mapper.readTree(saved).path("data").path("revision").asLong();
        mvc.perform(post("/api/web/publications/" + id + "/execute?revision=" + revision)
                        .with(authentication(auth)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.progress.results[0].status").value("PUBLISHED"));
        mvc.perform(post("/api/web/publications/" + id + "/execute?revision=" + revision)
                        .with(authentication(auth)).with(csrf())).andExpect(status().isConflict());
        mvc.perform(get("/api/web/publications/skill-target?namespace=publication-skills&slug=unified-example")
                        .with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.slug").value("unified-example"));
        var updateTask = read(mvc.perform(post("/api/web/publications").with(authentication(auth)).with(csrf())));
        String updateId = updateTask.path("id").asText();
        updateTask = read(mvc.perform(multipart("/api/web/publications/" + updateId + "/material?revision=0")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "example.zip", "application/zip", bytes.toByteArray()))
                .with(authentication(auth)).with(csrf())));
        var updateDraft = (com.fasterxml.jackson.databind.node.ObjectNode) updateTask.path("draft");
        updateDraft.put("namespace", "publication-skills").put("visibility", "PRIVATE").put("confirmWarnings", true);
        updateTask = saveDraft(updateId, updateTask, updateDraft, auth);
        mvc.perform(post("/api/web/publications/" + updateId + "/execute?revision=" + updateTask.path("revision").asLong())
                        .with(authentication(auth)).with(csrf())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").exists());
    }

    @Test
    void pluginImportAndSubmissionStayInOneTaskWithoutApprovingItsReview() throws Exception {
        String owner = "publication-plugin-owner";
        users.save(new com.iflytek.skillhub.domain.user.UserAccount(owner, owner, "plugin-pub@example.test", null));
        grant(owner, "SUPER_ADMIN");
        namespaces.save(new com.iflytek.skillhub.domain.namespace.Namespace("publication-plugins", "Plugins", owner));
        var auth = new UsernamePasswordAuthenticationToken(new PlatformPrincipal(owner, owner, "", "", "local", Set.of("SUPER_ADMIN")), null, List.of());
        var task = read(mvc.perform(post("/api/web/publications").with(authentication(auth)).with(csrf())));
        String id = task.path("id").asText();
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry(".claude-plugin/plugin.json"));
            zip.write("{\"name\":\"unified-plugin\",\"version\":\"1.0.0\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        task = read(mvc.perform(multipart("/api/web/publications/" + id + "/material?revision=0")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "plugin.zip", "application/zip", bytes.toByteArray()))
                .with(authentication(auth)).with(csrf())));
        var draft = (com.fasterxml.jackson.databind.node.ObjectNode) task.path("draft");
        draft.put("namespace", "publication-plugins").put("visibility", "PRIVATE");
        task = saveDraft(id, task, draft, auth);
        task = read(mvc.perform(post("/api/web/publications/" + id + "/preview?revision=" + task.path("revision").asLong())
                .with(authentication(auth)).with(csrf())));
        String candidate = task.path("progress").path("preview").path("candidates").get(0).path("id").asText();
        draft = (com.fasterxml.jackson.databind.node.ObjectNode) task.path("draft");
        draft.putArray("selections").addObject().put("candidateId", candidate).put("slug", "unified-plugin").put("visibility", "PRIVATE");
        task = saveDraft(id, task, draft, auth);
        for (int i = 0; i < 8 && !task.path("status").asText().equals("COMPLETE"); i++) {
            task = read(mvc.perform(post("/api/web/publications/" + id + "/execute?revision=" + task.path("revision").asLong())
                    .with(authentication(auth)).with(csrf())));
        }
        org.assertj.core.api.Assertions.assertThat(task.path("status").asText()).isEqualTo("COMPLETE");
        org.assertj.core.api.Assertions.assertThat(task.path("progress").path("results").get(0).path("status").asText()).isEqualTo("PENDING_REVIEW");
    }

    private com.fasterxml.jackson.databind.JsonNode read(org.springframework.test.web.servlet.ResultActions action) throws Exception {
        return mapper.readTree(action.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    @Test
    void softwareTaskRechecksRolesAndResumesAfterApplicationCreation() throws Exception {
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            if (policyState.findSingleton().isEmpty()) {
                String config = com.iflytek.skillhub.domain.software.SoftwarePolicyConfigJson.toJson(com.iflytek.skillhub.domain.software.SoftwarePolicyConfig.defaults());
                jdbc.update("INSERT INTO software_policy_revision (revision,schema_version,config,changed_by,change_reason,created_at) VALUES (1,1,? FORMAT JSON,'system','test seed',CURRENT_TIMESTAMP)", config);
                policyState.save(new com.iflytek.skillhub.domain.software.SoftwarePolicyState(policies.findById(1L).orElseThrow().getId()));
            }
            if (accounts.findSingleton().isEmpty()) accounts.save(new com.iflytek.skillhub.domain.software.SoftwareStorageAccount(0, 0));
        });
        String owner = "publication-software-owner";
        users.save(new com.iflytek.skillhub.domain.user.UserAccount(owner, owner, "software-pub@example.test", null));
        grant(owner, "SOFTWARE_ADMIN");
        var auth = new UsernamePasswordAuthenticationToken(new PlatformPrincipal(owner, owner, "", "", "local", Set.of("SOFTWARE_ADMIN")), null, List.of());
        var task = read(mvc.perform(post("/api/web/publications").with(authentication(auth)).with(csrf())));
        String id = task.path("id").asText();
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("readme.txt"));
            zip.write("A manually distributed application".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        task = read(mvc.perform(multipart("/api/web/publications/" + id + "/material?revision=0")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "software.zip", "application/zip", bytes.toByteArray()))
                .with(authentication(auth)).with(csrf())));
        var draft = (com.fasterxml.jackson.databind.node.ObjectNode) task.path("draft");
        draft.put("type", "SOFTWARE").put("slug", "unified-software").put("name", "Unified Software")
                .put("version", "2026 autumn").put("targetOs", "windows").put("targetArch", "x86_64").put("format", "zip");
        task = saveDraft(id, task, draft, auth);
        task = read(mvc.perform(post("/api/web/publications/" + id + "/execute?revision=" + task.path("revision").asLong())
                .with(authentication(auth)).with(csrf())));
        org.assertj.core.api.Assertions.assertThat(task.path("progress").path("results").size()).isEqualTo(1);
        long originalApplication = task.path("progress").path("results").get(0).path("resourceId").asLong();
        bindings.deleteAll(bindings.findByUserId(owner));
        mvc.perform(post("/api/web/publications/" + id + "/execute?revision=" + task.path("revision").asLong())
                .with(authentication(auth)).with(csrf())).andExpect(status().isForbidden());
        task = read(mvc.perform(get("/api/web/publications/" + id).with(authentication(auth))));
        grant(owner, "SOFTWARE_ADMIN");
        task = read(mvc.perform(post("/api/web/publications/" + id + "/material-upload?revision=" + task.path("revision").asLong())
                .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of("fileName", "replacement.zip", "byteSize", bytes.size(), "replace", true)))
                .with(authentication(auth)).with(csrf())));
        task = read(mvc.perform(put("/api/web/publications/" + id + "/material-upload/1?revision=" + task.path("revision").asLong())
                .contentType("application/octet-stream").content(bytes.toByteArray()).with(authentication(auth)).with(csrf())));
        task = read(mvc.perform(post("/api/web/publications/" + id + "/material-upload/complete?revision=" + task.path("revision").asLong())
                .with(authentication(auth)).with(csrf())));
        for (int i = 0; i < 20 && !task.path("status").asText().equals("COMPLETE"); i++) {
            task = read(mvc.perform(post("/api/web/publications/" + id + "/execute?revision=" + task.path("revision").asLong())
                    .with(authentication(auth)).with(csrf())));
        }
        org.assertj.core.api.Assertions.assertThat(task.path("status").asText()).isEqualTo("COMPLETE");
        org.assertj.core.api.Assertions.assertThat(task.path("progress").path("results").get(0).path("status").asText()).isEqualTo("PUBLISHED");
        org.assertj.core.api.Assertions.assertThat(task.path("progress").path("results").get(0).path("resourceId").asLong()).isEqualTo(originalApplication);
    }
    private com.fasterxml.jackson.databind.JsonNode saveDraft(String id, com.fasterxml.jackson.databind.JsonNode task,
            com.fasterxml.jackson.databind.JsonNode draft, UsernamePasswordAuthenticationToken auth) throws Exception {
        return read(mvc.perform(put("/api/web/publications/" + id).contentType("application/json")
                .content(mapper.writeValueAsString(java.util.Map.of("revision", task.path("revision").asLong(), "draft", draft)))
                .with(authentication(auth)).with(csrf())));
    }
    private void grant(String owner, String code) {
        var role = roles.findByCode(code).orElseGet(() -> {
            var value = new com.iflytek.skillhub.auth.entity.Role();
            org.springframework.test.util.ReflectionTestUtils.setField(value, "code", code);
            org.springframework.test.util.ReflectionTestUtils.setField(value, "name", code);
            return roles.save(value);
        });
        bindings.save(new com.iflytek.skillhub.auth.entity.UserRoleBinding(owner, role));
    }

    @Test
    void publishedSoftwareAppearsInUnifiedDiscoveryAndItsOwnersContentList() throws Exception {
        users.save(new com.iflytek.skillhub.domain.user.UserAccount("discovery-owner", "Owner", "discovery@example.test", null));
        grant("discovery-owner", "SOFTWARE_ADMIN");
        var app = new com.iflytek.skillhub.domain.software.SoftwareApplication("discovery-app", "Discovery application", "Unified discovery", null, null, "discovery-owner");
        app.publish(); applications.save(app);
        mvc.perform(get("/api/v1/catalog/search?q=discovery-app&type=SOFTWARE").with(authentication(actor("discovery-owner"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].type").value("SOFTWARE"))
                .andExpect(jsonPath("$.data.items[0].slug").value("discovery-app"));
        mvc.perform(get("/api/v1/catalog/search?q=discovery-app&type=SOFTWARE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        var token = new UsernamePasswordAuthenticationToken(new PlatformPrincipal("discovery-owner", "owner", "", "", "api_token", Set.of()), null, List.of());
        mvc.perform(get("/api/v1/catalog/search?q=discovery-app&type=SOFTWARE").with(authentication(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/web/publications/content").with(authentication(actor("discovery-owner"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].type").value("SOFTWARE"));
        mvc.perform(get("/api/web/publications/content").with(authentication(actor("another-content-owner"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void exposesPublicationContractForTheGeneratedWebClient() throws Exception {
        String contract = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(contract).contains("/api/web/publications/{id}/execute", "PublicationDraft");
        String output = System.getProperty("astronhub.openapi.output");
        if (output != null) java.nio.file.Files.writeString(java.nio.file.Path.of(output), contract);
    }

    @Test
    void interruptedMaterialUploadReplaysIdenticalChunksAndRejectsDifferentBytes() throws Exception {
        var auth = actor("chunk-publisher");
        var task = read(mvc.perform(post("/api/web/publications").with(authentication(auth)).with(csrf())));
        String id = task.path("id").asText();
        task = read(mvc.perform(post("/api/web/publications/" + id + "/material-upload?revision=0")
                .contentType("application/json").content("{\"fileName\":\"setup.exe\",\"byteSize\":4}")
                .with(authentication(auth)).with(csrf())));
        long before = task.path("revision").asLong();
        byte[] binary = {'M', 'Z', 0, 1};
        task = read(mvc.perform(put("/api/web/publications/" + id + "/material-upload/1?revision=" + before)
                .contentType("application/octet-stream").content(binary).with(authentication(auth)).with(csrf())));
        long after = task.path("revision").asLong();
        mvc.perform(put("/api/web/publications/" + id + "/material-upload/1?revision=" + before)
                .contentType("application/octet-stream").content(binary).with(authentication(auth)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(after));
        mvc.perform(put("/api/web/publications/" + id + "/material-upload/1?revision=" + after)
                .contentType("application/octet-stream").content(new byte[]{'M', 'Z', 0, 2}).with(authentication(auth)).with(csrf()))
                .andExpect(status().isConflict());
        task = read(mvc.perform(post("/api/web/publications/" + id + "/material-upload/complete?revision=" + after)
                .with(authentication(auth)).with(csrf())));
        org.assertj.core.api.Assertions.assertThat(task.path("materialReady").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(task.path("draft").path("type").asText()).isEqualTo("SOFTWARE");
        task = read(mvc.perform(post("/api/web/publications/" + id + "/cancel?revision=" + task.path("revision").asLong())
                .with(authentication(auth)).with(csrf())));
        org.assertj.core.api.Assertions.assertThat(task.path("materialReady").asBoolean()).isFalse();
        org.assertj.core.api.Assertions.assertThat(task.path("fileName").asText()).isEqualTo("setup.exe");
    }
}
