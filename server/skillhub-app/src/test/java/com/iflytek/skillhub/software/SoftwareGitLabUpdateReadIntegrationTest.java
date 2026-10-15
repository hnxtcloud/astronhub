package com.iflytek.skillhub.software;

import static org.assertj.core.api.Assertions.*;
import com.iflytek.skillhub.domain.software.*;
import com.iflytek.skillhub.dto.software.*;
import com.iflytek.skillhub.service.SoftwareUpdateAppService;
import com.iflytek.skillhub.service.SoftwareUploadAppService;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Actual embedded Tomcat, firewall, HTTP, JPA and local stored bytes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "skillhub.software.storage.temp-dir=./target/sw-gitlab-tmp",
        "skillhub.storage.local.base-path=./target/sw-gitlab-base",
        "skillhub.builtin-skills.enabled=false"
})
@ActiveProfiles("test")
class SoftwareGitLabUpdateReadIntegrationTest {
    @LocalServerPort int port;
    @Autowired SoftwareUpdateAppService updates;
    @Autowired SoftwareUploadAppService uploads;
    @Autowired SoftwareApplicationRepository applications;
    @Autowired SoftwareReleaseRepository releases;
    @Autowired SoftwarePackageService packages;
    @Autowired SoftwarePolicyStateRepository policyState;
    @Autowired SoftwarePolicyRevisionRepository policies;
    @Autowired SoftwareStorageAccountRepository accounts;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void existingGitLabClientCanDiscoverAndReadExactEncodedOriginalAssets() throws Exception {
        var fixture = publish();
        String project = "apps%2F" + fixture.slug();
        var tags = get("/api/v4/projects/" + project + "/repository/tags");
        assertThat(tags.statusCode()).isEqualTo(200);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(tags.body());
        assertThat(json.isArray()).isTrue();
        assertThat(json.get(0).path("name").asText()).isEqualTo("v1.2.3");
        assertThat(json.get(0).path("release").path("description").asText()).isEqualTo("Original notes");
        var asset = get("/api/v4/projects/" + project + "/repository/files/dist%2Fapp%2Bdemo.zip/raw?ref=v1.2.3");
        assertThat(asset.statusCode()).isEqualTo(200);
        assertThat(asset.body()).isEqualTo("0123456789".getBytes(StandardCharsets.UTF_8));
        assertThat(asset.headers().firstValue("Cache-Control")).contains("no-store");
    }

    HttpResponse<byte[]> get(String path) throws Exception {
        return send(path, "GET");
    }
    String contextPath() { return ""; }
    URI uri(String path) { return URI.create("http://localhost:" + port + contextPath() + path); }
    HttpResponse<byte[]> send(String path, String method, String... headers) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).method(method, HttpRequest.BodyPublishers.noBody());
        if (headers.length > 0) builder.headers(headers);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void existingUpdaterReceivesHeadSingleAndMultipartRangeResponses() throws Exception {
        var fixture = publish();
        String url = uri("/api/v4/projects/apps%2F" + fixture.slug() + "/repository/files/dist%2Fapp%2Bdemo.zip/raw?ref=v1.2.3").toString();
        var head = client.send(HttpRequest.newBuilder(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.body()).isEmpty();
        assertThat(head.headers().firstValue("Content-Length")).contains("10");
        var single = client.send(HttpRequest.newBuilder(URI.create(url)).header("Range", "bytes=2-4").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(single.statusCode()).isEqualTo(206);
        assertThat(single.body()).isEqualTo("234".getBytes(StandardCharsets.US_ASCII));
        assertThat(single.headers().firstValue("Content-Range")).contains("bytes 2-4/10");
        var multi = client.send(HttpRequest.newBuilder(URI.create(url)).header("Range", "bytes=0-1,8-9").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(multi.statusCode()).isEqualTo(206);
        assertThat(multi.headers().firstValue("Content-Type").orElseThrow()).startsWith("multipart/byteranges; boundary=");
        assertThat(multi.headers().firstValue("Content-Length")).contains(String.valueOf(multi.body().length));
        String body = new String(multi.body(), StandardCharsets.US_ASCII);
        assertThat(body).contains("Content-Range: bytes 0-1/10\r\n\r\n01\r\n", "Content-Range: bytes 8-9/10\r\n\r\n89\r\n");
        String fixtureOutput = System.getProperty("astronhub.update.multipartFixture");
        if (fixtureOutput != null) {
            var destination = java.nio.file.Path.of(fixtureOutput);
            java.nio.file.Files.createDirectories(destination.toAbsolutePath().getParent());
            new com.fasterxml.jackson.databind.ObjectMapper().writeValue(destination.toFile(), java.util.Map.of(
                    "contentType", multi.headers().firstValue("Content-Type").orElseThrow(),
                    "bodyBase64", java.util.Base64.getEncoder().encodeToString(multi.body()), "expected", "0189"));
        }
    }

    @Test
    void publishedBytesHaveStableSameOriginAssetAndGitLabWebRawAddresses() throws Exception {
        var fixture = publish();
        var fixed = get("/releases/" + fixture.slug() + "/" + fixture.releaseId() + "/assets/dist/app%2Bdemo.zip");
        assertThat(fixed.statusCode()).isEqualTo(200);
        assertThat(fixed.body()).isEqualTo("0123456789".getBytes(StandardCharsets.UTF_8));
        assertThat(fixed.headers().firstValue("Location")).isEmpty();
        var web = get("/apps/" + fixture.slug() + "/-/raw/v1.2.3/dist/app%2Bdemo.zip");
        assertThat(web.statusCode()).isEqualTo(200);
        assertThat(web.body()).isEqualTo(fixed.body());
    }

    @Test
    void governanceIsRecheckedBeforeCachedHeadAndRangeReadsAndOldTokensHaveNoAuthority() throws Exception {
        var fixture = publish();
        String fixed = "/releases/" + fixture.slug() + "/" + fixture.releaseId() + "/assets/dist/app+demo.zip";
        String raw = "/api/v4/projects/apps%2F" + fixture.slug() + "/repository/files/dist%2Fapp%2Bdemo.zip/raw?ref=v1.2.3";
        String tag = "/api/v4/projects/apps%2F" + fixture.slug() + "/repository/tags";
        String etag = get(fixed).headers().firstValue("ETag").orElseThrow();
        assertThat(send(fixed, "GET", "If-None-Match", etag).statusCode()).isEqualTo(304);
        updates.setAnonymous(fixture.applicationId(), false, "admin");
        assertThat(send(raw, "GET", "PRIVATE-TOKEN", "legacy-client-token").statusCode()).isEqualTo(404);
        assertThat(send(fixed, "GET", "If-None-Match", etag).statusCode()).isEqualTo(404);
        assertThat(send(fixed, "HEAD", "If-None-Match", etag).statusCode()).isEqualTo(404);
        assertThat(send(fixed, "GET", "Range", "bytes=0-1").statusCode()).isEqualTo(404);
        assertThat(send("/api/v1/admin/software/apps", "GET", "PRIVATE-TOKEN", "legacy-client-token").statusCode()).isEqualTo(401);
        updates.setAnonymous(fixture.applicationId(), true, "admin");
        packages.takeDown(fixture.packageId());
        assertThat(get(tag).statusCode()).isEqualTo(404);
        assertThat(send(fixed, "GET", "If-None-Match", etag).statusCode()).isEqualTo(404);
        updates.withdraw(fixture.releaseId(), "admin");
        packages.restore(fixture.packageId());
        assertThat(get(fixed).statusCode()).isEqualTo(404);
    }

    @Test
    void selectedTargetControlsDiscoveryWhileHistoricalBytesRemainBoundToTheirApplication() throws Exception {
        var fixture = publish();
        var newer = version(fixture, "2.0.0", true);
        var draft = version(fixture, "3.0.0", false);
        String tag = "/api/v4/projects/apps%2F" + fixture.slug() + "/repository/tags";
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(json.readTree(get(tag).body()).get(0).path("name").asText()).isEqualTo("v1.2.3");
        updates.saveChannel(fixture.applicationId(), new SoftwareUpdateChannelRequest("windows-stable", "apps/" + fixture.slug(), "WINDOWS", "X86_64", "stable", "default", newer.id()), "admin");
        assertThat(json.readTree(get(tag).body())).hasSize(1);
        assertThat(json.readTree(get(tag).body()).get(0).path("name").asText()).isEqualTo("v2.0.0");
        String oldRaw = "/api/v4/projects/apps%2F" + fixture.slug() + "/repository/files/dist%2Fapp%2Bdemo.zip/raw?ref=v1.2.3";
        assertThat(get(oldRaw).statusCode()).isEqualTo(200);
        String fixed = "/releases/" + fixture.slug() + "/" + fixture.releaseId() + "/assets/dist/app+demo.zip";
        assertThat(get("/releases/" + fixture.slug() + "/" + draft.id() + "/assets/dist/app+demo.zip").statusCode()).isEqualTo(404);
        var other = publish();
        assertThat(get("/releases/" + other.slug() + "/" + fixture.releaseId() + "/assets/dist/app+demo.zip").statusCode()).isEqualTo(404);
        updates.saveChannel(fixture.applicationId(), new SoftwareUpdateChannelRequest("windows-stable", "apps/" + fixture.slug(), "WINDOWS", "X86_64", "stable", "default", null), "admin");
        assertThat(get(tag).statusCode()).isEqualTo(404);
        assertThat(get(oldRaw).statusCode()).isEqualTo(404);
        assertThat(get(fixed).statusCode()).isEqualTo(200);
        tx.executeWithoutResult(status -> { var app = applications.findById(fixture.applicationId()).orElseThrow(); app.takeDown(); applications.save(app); });
        assertThat(get(fixed).statusCode()).isEqualTo(404);
    }

    @Test
    void theEncodedSlashExceptionCannotWeakenOrdinaryRoutesOrAliasAssetPaths() throws Exception {
        var fixture = publish();
        String prefix = "/api/v4/projects/apps%2F" + fixture.slug() + "/repository/files/";
        for (String file : java.util.List.of("dist%252Fapp.zip", "dist%2F%2E%2E%2Fapp.zip", "dist%2F%2Fapp.zip", "dist%5Capp.zip", "dist%2Fapp%00.zip", "dist%2Fapp%FF.zip", "dist%2Fapp%2Ezip")) {
            assertThat(get(prefix + file + "/raw?ref=v1.2.3").statusCode()).as(file).isBetween(400, 499);
        }
        assertThat(get("/api/v1/software/apps/foo%2Fbar").statusCode()).isBetween(400, 499);
        assertThat(send(prefix + "dist%2Fapp%2Bdemo.zip/raw?ref=v1.2.3", "POST").statusCode()).isBetween(400, 499);
        assertThat(get(prefix + "dist%2Fapp%2Bdemo.zip/raw?ref=v1.2.3&ref=v1.2.3").statusCode()).isEqualTo(400);
        assertThat(get(prefix + "dist%2Fapp+demo.zip/raw?ref=v1.2.3").statusCode()).isEqualTo(200);
        assertThat(send("/api/v4/projects/apps%2F" + fixture.slug() + "/repository/tags", "GET", "X-Forwarded-Prefix", "/corp/hub").statusCode()).isEqualTo(200);
    }

    @Test
    void ifRangeIsEvaluatedBeforeUnsatisfiableRangesAndRangeWorkHasExplicitBounds() throws Exception {
        var fixture = publish();
        String path = "/releases/" + fixture.slug() + "/" + fixture.releaseId() + "/assets/dist/app+demo.zip";
        String etag = get(path).headers().firstValue("ETag").orElseThrow();
        assertThat(send(path, "GET", "Range", "bytes=-2").body()).isEqualTo("89".getBytes(StandardCharsets.US_ASCII));
        var stale = send(path, "GET", "Range", "bytes=99-100", "If-Range", "\"stale-object\"");
        assertThat(stale.statusCode()).isEqualTo(200);
        assertThat(stale.body()).isEqualTo("0123456789".getBytes(StandardCharsets.US_ASCII));
        var unsatisfiable = send(path, "GET", "Range", "bytes=99-100", "If-Range", etag);
        assertThat(unsatisfiable.statusCode()).isEqualTo(416);
        assertThat(unsatisfiable.headers().firstValue("Content-Range")).contains("bytes */10");
        var many = send(path, "GET", "Range", "bytes=" + String.join(",", java.util.Collections.nCopies(17, "0-0")));
        assertThat(many.statusCode()).isEqualTo(200);
        assertThat(many.body()).isEqualTo(stale.body());
    }

    @Test
    void malformedPercentEscapesAreRejectedByTheActualHttpContainer() throws Exception {
        try (var socket = new java.net.Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            String request = "GET " + contextPath() + "/api/v4/projects/apps%2Funknown/repository/files/%ZZ/raw?ref=v1.2.3 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            String status = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1)).readLine();
            assertThat(status).contains(" 400 ");
        }
    }

    private SoftwareUpdateReleaseResponse version(Fixture fixture, String version, boolean publish) {
        var draft = updates.create(new SoftwareUpdateCreateRequest(fixture.catalogId(), version, "v" + version, "WINDOWS", "X86_64", "stable", "default", "ELECTRON", null), "admin");
        var linked = updates.linkPackage(draft.id(), fixture.packageId(), "dist/app+demo.zip", "admin");
        byte[] yaml = ("version: " + version + "\nfiles:\n - url: app%2Bdemo.zip\n   size: 10\n   sha512: " + linked.sha512Base64() + "\n").getBytes(StandardCharsets.UTF_8);
        updates.uploadAsset(draft.id(), "dist/latest.yml", "METADATA", yaml.length, new ByteArrayInputStream(yaml), "admin");
        return publish ? updates.publish(draft.id(), "admin") : draft;
    }

    Fixture publish() {
        tx.executeWithoutResult(status -> {
            if (policyState.findSingleton().isEmpty()) {
                String config = SoftwarePolicyConfigJson.toJson(SoftwarePolicyConfig.defaults());
                jdbc.update("INSERT INTO software_policy_revision (revision,schema_version,config,changed_by,change_reason,created_at) VALUES (1,1,'" + config + "' FORMAT JSON,'system','test seed',CURRENT_TIMESTAMP)");
                policyState.save(new SoftwarePolicyState(policies.findById(1L).orElseThrow().getId()));
            }
            if (accounts.findSingleton().isEmpty()) accounts.save(new SoftwareStorageAccount(0, 0));
        });
        String slug = "gitlab-" + System.nanoTime();
        long app = tx.execute(status -> { var entity = new SoftwareApplication(slug, "GitLab App", null, null, null, "admin"); entity.publish(); return applications.save(entity).getId(); });
        long catalog = tx.execute(status -> releases.save(new SoftwareRelease(app, "Human Version", "Original notes", "admin")).getId());
        byte[] binary = "0123456789".getBytes(StandardCharsets.UTF_8);
        var upload = uploads.createSession(new SoftwareUploadSessionCreateRequest(catalog, "app.zip", "ZIP", "WINDOWS", "X86_64", null, null, "1.2.3", 10L), "admin");
        uploads.putChunk(upload.id(), 1, 10L, new ByteArrayInputStream(binary), "admin");
        long packageId = uploads.complete(upload.id(), "admin").packageRevisionId();
        packages.confirm(packageId, "admin"); packages.publish(packageId);
        var snapshot = updates.create(new SoftwareUpdateCreateRequest(catalog, "1.2.3", "v1.2.3", "WINDOWS", "X86_64", "stable", "default", "ELECTRON", null), "admin");
        var linked = updates.linkPackage(snapshot.id(), packageId, "dist/app+demo.zip", "admin");
        byte[] yaml = ("version: 1.2.3\nfiles:\n - url: app%2Bdemo.zip\n   size: 10\n   sha512: " + linked.sha512Base64() + "\n").getBytes(StandardCharsets.UTF_8);
        updates.uploadAsset(snapshot.id(), "dist/latest.yml", "METADATA", yaml.length, new ByteArrayInputStream(yaml), "admin");
        updates.publish(snapshot.id(), "admin");
        updates.setAnonymous(app, true, "admin");
        updates.saveChannel(app, new SoftwareUpdateChannelRequest("windows-stable", "apps/" + slug, "WINDOWS", "X86_64", "stable", "default", snapshot.id()), "admin");
        return new Fixture(app, snapshot.id(), packageId, slug, catalog);
    }
    record Fixture(long applicationId, long releaseId, long packageId, String slug, long catalogId) { }
}
