package com.iflytek.skillhub.software;

import static org.assertj.core.api.Assertions.*;
import com.iflytek.skillhub.auth.entity.*;
import com.iflytek.skillhub.auth.repository.*;
import com.iflytek.skillhub.auth.token.ApiTokenService;
import com.iflytek.skillhub.domain.software.*;
import com.iflytek.skillhub.domain.user.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Real bearer authentication and application-bound publishing over embedded HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "skillhub.builtin-skills.enabled=false", "skillhub.software.storage.temp-dir=./target/sw-machine-tmp",
        "skillhub.storage.local.base-path=./target/sw-machine-base"})
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class SoftwareMachinePublishingIntegrationTest {
    @LocalServerPort int port;
    @Autowired ApiTokenService tokens;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleBindingRepository bindings;
    @Autowired SoftwareApplicationRepository apps;
    @Autowired TransactionTemplate tx;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired SoftwarePolicyStateRepository policyState;
    @Autowired SoftwarePolicyRevisionRepository policies;
    @Autowired SoftwareStorageAccountRepository accounts;
    @Autowired SoftwareInspectionTaskService inspections;
    @Autowired ApiTokenRepository tokenRepository;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
    final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void selfRequestedScopeDoesNotGrantAnAdminTokenApplicationPublishingRights() throws Exception {
        String owner = "publisher-" + System.nanoTime();
        long app = tx.execute(status -> {
            users.save(new UserAccount(owner, "Publisher", null, ""));
            if (roles.findByCode("SOFTWARE_ADMIN").isEmpty()) jdbc.update("INSERT INTO role(code,name,is_system,created_at) VALUES('SOFTWARE_ADMIN','Software admin',true,CURRENT_TIMESTAMP)");
            Role role = roles.findByCode("SOFTWARE_ADMIN").orElseThrow();
            bindings.save(new UserRoleBinding(owner, role));
            return apps.save(new SoftwareApplication("machine-" + System.nanoTime(), "Machine", null, null, null, owner)).getId();
        });
        var token = tokens.createToken(owner, "self", "[\"software:publish\"]");
        normalizeH2Scopes(token.entity().getId());
        assertThat(call(app, "GET", "/releases", null, token.rawToken()).statusCode()).isEqualTo(403);
        assertThat(call(app, "GET", "/releases", null, null).statusCode()).isEqualTo(401);
        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(owner, "Publisher", null, "", "local", java.util.Set.of("SOFTWARE_ADMIN"));
        var session = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SOFTWARE_ADMIN")));
        var created = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/software/apps/" + app + "/publishers")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(session))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"name\":\"ci\",\"expiresAt\":\"2030-01-01T00:00:00Z\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
        var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getContentAsString()).path("data");
        String issued = data.path("rawToken").asText();
        normalizeH2Scopes(data.path("publisher").path("tokenId").asLong());
        assertThat(issued).startsWith("sk_");
        assertThat(created.getHeader("Cache-Control")).contains("no-store");
        data(call(app, "GET", "/releases", null, issued));
    }

    HttpResponse<String> call(long app, String method, String path, String json, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/software-publishing/apps/" + app + path))
                .header("Content-Type", "application/json").method(method,
                        json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void fullArtifactSetCannotPublishUntilInspectionConfirmationAndMetadataValidationComplete() throws Exception {
        var f = fixture();
        var release = data(call(f.app, "POST", "/releases", "{\"displayVersion\":\"Human release\",\"releaseNotes\":\"Original notes\"}", f.token));
        long catalog = release.path("id").asLong();
        String uploadBody = "{\"releaseId\":" + catalog + ",\"fileName\":\"app.exe\",\"format\":\"EXE\",\"targetOs\":\"WINDOWS\",\"targetArch\":\"X86_64\",\"originalFullVersion\":\"1.2.3\",\"byteSize\":3}";
        long upload = data(call(f.app, "POST", "/uploads", uploadBody, f.token)).path("id").asLong();
        assertThat(raw(f, "PUT", "/uploads/" + upload + "/chunks/1", "abc".getBytes()).statusCode()).isEqualTo(200);
        assertThat(raw(f, "PUT", "/uploads/" + upload + "/chunks/1", "abc".getBytes()).statusCode()).isEqualTo(200);
        long pkg = data(call(f.app, "POST", "/uploads/" + upload + "/complete", null, f.token)).path("packageRevisionId").asLong();
        assertThat(data(call(f.app, "POST", "/uploads/" + upload + "/complete", null, f.token)).path("packageRevisionId").asLong()).isEqualTo(pkg);
        assertThat(call(f.app, "POST", "/packages/" + pkg + "/confirm", "{}", f.token).statusCode()).isEqualTo(409);
        assertThat(data(call(f.app, "GET", "/packages/" + pkg, null, f.token)).path("inspection").path("task").path("status").asText()).isEqualTo("PENDING");
        var task = inspections.claim("test-inspector", java.time.Instant.now());
        assertThat(task.packageRevisionId()).isEqualTo(pkg);
        inspections.submitResult(task.taskId(), "test-inspector", task.attemptToken(), new SoftwareInspectionTaskService.ResultSubmission(
                SoftwareInspectionOutcome.SUCCEEDED, null, "{\"version\":\"1.2.3\"}", "fixture", "1", java.time.Instant.now(), java.time.Instant.now()));
        assertThat(call(f.app, "POST", "/packages/" + pkg + "/confirm", "{}", f.token).statusCode()).isEqualTo(200);
        assertThat(call(f.app, "POST", "/packages/" + pkg + "/publish", null, f.token).statusCode()).isEqualTo(200);
        String update = "{\"catalogReleaseId\":" + catalog + ",\"machineVersion\":\"1.2.3\",\"tag\":\"v1.2.3\",\"targetOs\":\"WINDOWS\",\"targetArch\":\"X86_64\",\"channel\":\"stable\",\"variant\":\"default\",\"protocol\":\"ELECTRON\"}";
        long snapshot = data(call(f.app, "POST", "/update-releases", update, f.token)).path("id").asLong();
        var installer = data(call(f.app, "POST", "/update-releases/" + snapshot + "/package-assets", "{\"packageRevisionId\":" + pkg + ",\"relativePath\":\"dist/app.exe\"}", f.token));
        assertThat(call(f.app, "POST", "/update-releases/" + snapshot + "/publish", null, f.token).statusCode()).isEqualTo(400);
        byte[] yaml = ("version: 1.2.3\nfiles:\n - url: app.exe\n   size: 3\n   sha512: " + installer.path("sha512Base64").asText() + "\n").getBytes();
        assertThat(raw(f, "POST", "/update-releases/" + snapshot + "/assets?path=dist/latest.yml&role=METADATA", yaml).statusCode()).isEqualTo(200);
        data(call(f.app, "POST", "/update-releases/" + snapshot + "/validate", null, f.token));
        assertThat(data(call(f.app, "POST", "/update-releases/" + snapshot + "/publish", null, f.token)).path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(call(f.app, "POST", "/update-releases/" + snapshot + "/publish", null, f.token).statusCode()).isEqualTo(200);
        assertThat(raw(f, "POST", "/update-releases/" + snapshot + "/assets?path=notes.txt&role=OTHER", new byte[]{1}).statusCode()).isEqualTo(409);
        var other = fixture();
        assertThat(call(other.app, "POST", "/uploads/" + upload + "/complete", null, other.token).statusCode()).isEqualTo(403);
        assertThat(call(other.app, "GET", "/packages/" + pkg, null, other.token).statusCode()).isEqualTo(403);
        assertThat(call(other.app, "GET", "/update-releases/" + snapshot, null, other.token).statusCode()).isEqualTo(403);
        assertThat(call(other.app, "POST", "/update-releases", update, other.token).statusCode()).isEqualTo(403);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var manifest = mapper.createObjectNode().put("displayVersion", "Human release").put("releaseNotes", "Original notes").put("confirmMetadata", true);
        manifest.set("update", mapper.readTree(update));
        manifest.putArray("installers").addObject().put("file", "app.exe").put("path", "dist/app.exe").put("format", "EXE").put("originalFullVersion", "1.2.3");
        manifest.putArray("assets").addObject().put("file", "latest.yml").put("path", "dist/latest.yml").put("role", "METADATA");
        java.nio.file.Files.write(temporary.resolve("app.exe"), "abc".getBytes());
        java.nio.file.Files.write(temporary.resolve("latest.yml"), yaml);
        mapper.writeValue(temporary.resolve("manifest.json").toFile(), manifest);
        assertThat(tool(f)).isZero();
        assertThat(tool(f)).isZero();
        assertThat(java.nio.file.Files.readString(temporary.resolve("state.json"))).doesNotContain(f.token);
        java.nio.file.Files.writeString(temporary.resolve("app.exe"), "xyz");
        assertThat(tool(f)).isEqualTo(1);
    }

    @Test
    void liveGrantOwnerRoleAndCredentialAreRecheckedAndManagementStaysSessionOnly() throws Exception {
        var f = fixture();
        assertThat(call(f.app + 100000L, "GET", "/releases", null, f.token).statusCode()).isEqualTo(403);
        assertThat(http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/software/apps"))
                .header("Authorization", "Bearer " + f.token).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/software-publishing/apps/" + f.app + "/releases")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(f.session)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        tx.executeWithoutResult(status -> bindings.findByUserId(f.owner).forEach(bindings::delete));
        assertThat(call(f.app, "GET", "/releases", null, f.token).statusCode()).isEqualTo(403);
        tx.executeWithoutResult(status -> bindings.save(new UserRoleBinding(f.owner, roles.findByCode("SOFTWARE_ADMIN").orElseThrow())));
        tx.executeWithoutResult(status -> { var user = users.findById(f.owner).orElseThrow(); user.setStatus(UserStatus.DISABLED); users.save(user); });
        assertThat(call(f.app, "GET", "/releases", null, f.token).statusCode()).isEqualTo(401);
        tx.executeWithoutResult(status -> { var user = users.findById(f.owner).orElseThrow(); user.setStatus(UserStatus.ACTIVE); users.save(user); });
        tx.executeWithoutResult(status -> { var token = tokenRepository.findById(f.tokenId).orElseThrow(); token.setExpiresAt(java.time.Instant.parse("2000-01-01T00:00:00Z")); tokenRepository.save(token); });
        assertThat(call(f.app, "GET", "/releases", null, f.token).statusCode()).isEqualTo(401);
        tx.executeWithoutResult(status -> { var token = tokenRepository.findById(f.tokenId).orElseThrow(); token.setExpiresAt(java.time.Instant.parse("2030-01-01T00:00:00Z")); tokenRepository.save(token); });
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/software/apps/" + f.app + "/publishers/" + f.tokenId)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(f.session))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(call(f.app, "GET", "/releases", null, f.token).statusCode()).isEqualTo(401);
    }

    Fixture fixture() throws Exception {
        String owner = "machine-owner-" + System.nanoTime();
        long app = tx.execute(status -> {
            users.save(new UserAccount(owner, "Publisher", null, ""));
            if (roles.findByCode("SOFTWARE_ADMIN").isEmpty()) jdbc.update("INSERT INTO role(code,name,is_system,created_at) VALUES('SOFTWARE_ADMIN','Software admin',true,CURRENT_TIMESTAMP)");
            bindings.save(new UserRoleBinding(owner, roles.findByCode("SOFTWARE_ADMIN").orElseThrow()));
            if (policyState.findSingleton().isEmpty()) {
                String config = SoftwarePolicyConfigJson.toJson(SoftwarePolicyConfig.defaults());
                jdbc.update("INSERT INTO software_policy_revision (revision,schema_version,config,changed_by,change_reason,created_at) VALUES (1,1,'" + config + "' FORMAT JSON,'system','test',CURRENT_TIMESTAMP)");
                policyState.save(new SoftwarePolicyState(policies.findById(1L).orElseThrow().getId()));
            }
            if (accounts.findSingleton().isEmpty()) accounts.save(new SoftwareStorageAccount(0, 0));
            var entity = new SoftwareApplication("machine-" + System.nanoTime(), "Machine", null, null, null, owner);
            entity.publish(); return apps.save(entity).getId();
        });
        var principal = new com.iflytek.skillhub.auth.rbac.PlatformPrincipal(owner, "Publisher", null, "", "local", java.util.Set.of("SOFTWARE_ADMIN"));
        var session = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SOFTWARE_ADMIN")));
        var created = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/software/apps/" + app + "/publishers")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(session))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"name\":\"ci\",\"expiresAt\":\"2030-01-01T00:00:00Z\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
        var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getContentAsString()).path("data");
        normalizeH2Scopes(data.path("publisher").path("tokenId").asLong());
        return new Fixture(app, owner, data.path("rawToken").asText(), data.path("publisher").path("tokenId").asLong(), session);
    }
    private void normalizeH2Scopes(long id) {
        // H2 JSONB domain binds String as a JSON string. PostgreSQL's native jsonb
        // receives the intended array. Keep the test fixture native, as policy seeds do.
        jdbc.update("UPDATE api_token SET scope_json='[\"software:publish\"]' FORMAT JSON WHERE id=?", id);
    }
    com.fasterxml.jackson.databind.JsonNode data(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body()).path("data");
    }
    HttpResponse<String> raw(Fixture f, String method, String path, byte[] body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/software-publishing/apps/" + f.app + path))
                .header("Authorization", "Bearer " + f.token).header("Content-Type", "application/octet-stream")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    record Fixture(long app, String owner, String token, long tokenId, org.springframework.security.core.Authentication session) { }
    int tool(Fixture f) throws Exception {
        var process = new ProcessBuilder("python", java.nio.file.Path.of("../../scripts/publish-software-release.py").toAbsolutePath().toString(),
                "--server", "http://localhost:" + port, "--app", String.valueOf(f.app), "--manifest", temporary.resolve("manifest.json").toString(),
                "--state", temporary.resolve("state.json").toString(), "--publish").redirectErrorStream(true);
        process.environment().put("ASTRONHUB_PUBLISH_TOKEN", f.token);
        var running = process.start();
        boolean done = running.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        if (!done) running.destroyForcibly();
        assertThat(done).isTrue();
        String output = new String(running.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(output).doesNotContain(f.token);
        if (running.exitValue() != 0 && !output.contains("Resume state")) fail(output);
        return running.exitValue();
    }

    @Test
    void manualZipRequiresExplicitMetadataInsteadOfInventingAnInspectionTask() throws Exception {
        var f = fixture();
        long catalog = data(call(f.app, "POST", "/releases", "{\"displayVersion\":\"ZIP release\"}", f.token)).path("id").asLong();
        String uploadBody = "{\"releaseId\":" + catalog + ",\"fileName\":\"app.zip\",\"format\":\"ZIP\",\"targetOs\":\"MACOS\",\"targetArch\":\"X86_64\",\"originalFullVersion\":\"1.2.3\",\"byteSize\":3}";
        long upload = data(call(f.app, "POST", "/uploads", uploadBody, f.token)).path("id").asLong();
        data(raw(f, "PUT", "/uploads/" + upload + "/chunks/1", "zip".getBytes()));
        long pkg = data(call(f.app, "POST", "/uploads/" + upload + "/complete", null, f.token)).path("packageRevisionId").asLong();
        assertThat(call(f.app, "POST", "/packages/" + pkg + "/confirm", "{}", f.token).statusCode()).isEqualTo(400);
        data(call(f.app, "POST", "/packages/" + pkg + "/confirm", "{\"rawVersionFields\":{\"version\":\"1.2.3\"}}", f.token));
    }

    @Test
    void staleUsageTelemetryCannotRestoreRevokedCredentialsOrRewriteScopes() throws Exception {
        var f = fixture();
        var stale = tokens.validateToken(f.token).orElseThrow();
        tokens.touchLastUsed(stale);
        assertThat(tokens.validateToken(f.token).orElseThrow().getScopeJson()).isEqualTo("[\"software:publish\"]");
        tokens.revokeToken(f.tokenId, f.owner);
        tokens.touchLastUsed(stale);
        assertThat(tokens.validateToken(f.token)).isEmpty();
    }

    @Test
    void controlledToolUploadsConfirmsAndPublishesAnEntireOriginalArtifactSet() throws Exception {
        var f = fixture();
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("readme.txt")); zip.write("synthetic contract fixture".getBytes()); zip.closeEntry();
        }
        byte[] installer = bytes.toByteArray();
        String sha512 = java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-512").digest(installer));
        String yaml = "version: 7.0.0\nfiles:\n - url: app.zip\n   size: " + installer.length + "\n   sha512: " + sha512 + "\n";
        java.nio.file.Files.write(temporary.resolve("app.zip"), installer);
        java.nio.file.Files.writeString(temporary.resolve("latest-mac.yml"), yaml);
        String manifest = """
                {"displayVersion":"Tool release","confirmMetadata":true,
                 "update":{"machineVersion":"7.0.0","tag":"v7.0.0","targetOs":"MACOS","targetArch":"X86_64","channel":"stable","variant":"default","protocol":"ELECTRON"},
                 "installers":[{"file":"app.zip","path":"dist/app.zip","format":"ZIP","originalFullVersion":"7.0.0","rawVersionFields":{"version":"7.0.0"}}],
                 "assets":[{"file":"latest-mac.yml","path":"dist/latest-mac.yml","role":"METADATA"}]}
                """;
        java.nio.file.Files.writeString(temporary.resolve("manifest.json"), manifest);
        assertThat(tool(f)).isZero();
        assertThat(tool(f)).isZero();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(java.nio.file.Files.readString(temporary.resolve("state.json"))).path("snapshotId").asLong();
        var snapshot = data(call(f.app, "GET", "/update-releases/" + id, null, f.token));
        assertThat(snapshot.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(snapshot.path("assets").size()).isEqualTo(2);
    }

    @Test
    void machineUploadsPreserveCapacityAdmissionAndFailedBasicInspectionCannotBeConfirmed() throws Exception {
        var f = fixture();
        long release = data(call(f.app, "POST", "/releases", "{\"displayVersion\":\"Rejected upload\"}", f.token)).path("id").asLong();
        String input = "{\"releaseId\":" + release + ",\"fileName\":\"failed.exe\",\"format\":\"EXE\",\"targetOs\":\"WINDOWS\",\"targetArch\":\"X86_64\",\"originalFullVersion\":\"1.0.0\",\"byteSize\":%d}";
        assertThat(call(f.app, "POST", "/uploads", input.formatted(5L * 1024 * 1024 * 1024 + 1), f.token).statusCode()).isEqualTo(400);
        long upload = data(call(f.app, "POST", "/uploads", input.formatted(3), f.token)).path("id").asLong();
        assertThat(raw(f, "PUT", "/uploads/" + upload + "/chunks/1", new byte[]{1, 2}).statusCode()).isEqualTo(400);
        data(raw(f, "PUT", "/uploads/" + upload + "/chunks/1", new byte[]{1, 2, 3}));
        long pkg = data(call(f.app, "POST", "/uploads/" + upload + "/complete", null, f.token)).path("packageRevisionId").asLong();
        var task = inspections.claim("failure-inspector", java.time.Instant.now());
        assertThat(task.packageRevisionId()).isEqualTo(pkg);
        inspections.submitResult(task.taskId(), "failure-inspector", task.attemptToken(), new SoftwareInspectionTaskService.ResultSubmission(
                SoftwareInspectionOutcome.BASIC_FAILED, "format_invalid", null, "fixture", "1", java.time.Instant.now(), java.time.Instant.now()));
        assertThat(call(f.app, "POST", "/packages/" + pkg + "/confirm", "{\"rawVersionFields\":{\"version\":\"1.0.0\"}}", f.token).statusCode()).isEqualTo(409);
        var status = data(call(f.app, "GET", "/packages/" + pkg, null, f.token));
        assertThat(status.path("inspection").path("task").path("status").asText()).isEqualTo("BASIC_FAILED");
        assertThat(status.path("inspection").path("attempts").get(0).path("errorCode").asText()).isEqualTo("format_invalid");
    }
}
