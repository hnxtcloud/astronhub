package com.iflytek.skillhub.software;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import com.iflytek.skillhub.dto.software.*;
import com.iflytek.skillhub.domain.software.*;
import org.junit.jupiter.api.Test;

/** Native feed aliases on the real governed publication and HTTP boundary. */
class SoftwareNativeUpdateFeedIntegrationTest extends SoftwareGitLabUpdateReadIntegrationTest {
    @Test
    void genericMetadataPinsOriginalInstallerToItsImmutableSnapshot() throws Exception {
        var fixture = publish();
        var response = get("/updates/" + fixture.slug() + "/windows-stable/latest-mac.yml");
        assertThat(response.statusCode()).isEqualTo(200);
        String yaml = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(yaml).contains("/releases/" + fixture.slug() + "/" + fixture.releaseId() + "/assets/dist/app%2Bdemo.zip");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(response.body()));
        assertThat(response.headers().firstValue("ETag")).contains("\"" + digest + "\"");
        String path = "/updates/" + fixture.slug() + "/windows-stable/latest-mac.yml";
        var head = send(path, "HEAD");
        assertThat(head.body()).isEmpty();
        assertThat(head.headers().firstValue("Content-Length")).contains(String.valueOf(response.body().length));
        var range = send(path, "GET", "Range", "bytes=0-9");
        assertThat(range.statusCode()).isEqualTo(206);
        assertThat(range.body()).isEqualTo(java.util.Arrays.copyOf(response.body(), 10));
        var original = get("/releases/" + fixture.slug() + "/" + fixture.releaseId() + "/assets/dist/latest.yml");
        assertThat(new String(original.body(), StandardCharsets.UTF_8)).contains("url: app%2Bdemo.zip").doesNotContain("/releases/");
    }

    @Test
    void nativePackageFormatCannotBeRenamedToMasqueradeAsMacZip() {
        var fixture = publish();
        byte[] binary = "abcdefghij".getBytes(StandardCharsets.UTF_8);
        var upload = uploads.createSession(new com.iflytek.skillhub.dto.software.SoftwareUploadSessionCreateRequest(
                fixture.catalogId(), "app.dmg", "DMG", "MACOS", "X86_64", null, null, "1.2.3", 10L), "admin");
        uploads.putChunk(upload.id(), 1, 10L, new java.io.ByteArrayInputStream(binary), "admin");
        long pkg = uploads.complete(upload.id(), "admin").packageRevisionId();
        packages.confirm(pkg, "admin"); packages.publish(pkg);
        var draft = updates.create(new com.iflytek.skillhub.dto.software.SoftwareUpdateCreateRequest(
                fixture.catalogId(), "4.0.0", "v4.0.0", "MACOS", "X86_64", "stable", "default", "ELECTRON", null), "admin");
        assertThatThrownBy(() -> updates.linkPackage(draft.id(), pkg, "pretend.zip", "admin"))
                .hasMessageContaining("error.software.update.invalid");
    }

    @Test
    void genericProjectionUsesYamlMediaTypeEvenWhenOriginalMetadataWasJson() throws Exception {
        var fixture = publish();
        var draft = updates.create(new SoftwareUpdateCreateRequest(fixture.catalogId(), "5.0.0", "v5.0.0", "MACOS",
                "X86_64", "stable", "default", "ELECTRON", null), "admin");
        var installer = updates.linkPackage(draft.id(), fixture.packageId(), "dist/app+demo.zip", "admin");
        byte[] json = ("{\"version\":\"5.0.0\",\"files\":[{\"url\":\"app%2Bdemo.zip\",\"size\":10,\"sha512\":\"" + installer.sha512Base64() + "\"}]}")
                .getBytes(StandardCharsets.UTF_8);
        updates.uploadAsset(draft.id(), "dist/native.json", "METADATA", json.length, new ByteArrayInputStream(json), "admin");
        updates.publish(draft.id(), "admin");
        updates.saveChannel(fixture.applicationId(), new SoftwareUpdateChannelRequest("json-native", "json/" + fixture.slug(),
                "MACOS", "X86_64", "stable", "default", draft.id()), "admin");
        var feed = get("/updates/" + fixture.slug() + "/json-native/latest-mac.yml");
        assertThat(feed.statusCode()).isEqualTo(200);
        assertThat(feed.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/yaml");
    }

    @Test
    void installedNativeClientsCanParseAndDownloadTheLiveSyntheticFeeds() throws Exception {
        String electron = System.getProperty("astronhub.update.electronRoot");
        String baize = System.getProperty("astronhub.update.baizeRoot");
        org.junit.jupiter.api.Assumptions.assumeTrue(electron != null && baize != null, "Explicit installed client roots required");
        var fixture = publish();
        baize(fixture, "stable", "1.2.3");
        baize(fixture, "preview", "2.0.0-beta.1");
        genericPreview(fixture);
        var nativeLog = java.nio.file.Path.of("target/update-contract/native-feed" + contextPath().replace('/', '-') + ".log");
        java.nio.file.Files.createDirectories(nativeLog.toAbsolutePath().getParent());
        var process = new ProcessBuilder("node", java.nio.file.Path.of("../../scripts/tests/native-feed-contract.cjs").toAbsolutePath().toString(),
                electron, baize, uri("/updates/" + fixture.slug() + "/windows-stable/").toString(),
                uri("/updates/" + fixture.slug() + "/baize-stable/latest.json").toString(),
                uri("/updates/" + fixture.slug() + "/mac-preview/").toString(),
                uri("/updates/" + fixture.slug() + "/baize-preview/latest.json").toString())
                .redirectErrorStream(true).redirectOutput(nativeLog.toFile()).start();
        boolean completed = process.waitFor(45, java.util.concurrent.TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        assertThat(completed).isTrue();
        String output = java.nio.file.Files.readString(nativeLog);
        System.out.print(output);
        assertThat(process.exitValue()).withFailMessage(output).isZero();
    }

    void genericPreview(Fixture fixture) {
        var draft = updates.create(new SoftwareUpdateCreateRequest(fixture.catalogId(), "2.0.0-beta.1", "v2.0.0-beta.1", "MACOS",
                "X86_64", "preview", "default", "ELECTRON", null), "admin");
        var installer = updates.linkPackage(draft.id(), fixture.packageId(), "dist/app+demo.zip", "admin");
        byte[] yaml = ("version: 2.0.0-beta.1\nfiles:\n - url: app%2Bdemo.zip\n   size: 10\n   sha512: " + installer.sha512Base64() + "\n").getBytes(StandardCharsets.UTF_8);
        updates.uploadAsset(draft.id(), "dist/preview-mac.yml", "METADATA", yaml.length, new ByteArrayInputStream(yaml), "admin");
        updates.publish(draft.id(), "admin");
        updates.saveChannel(fixture.applicationId(), new SoftwareUpdateChannelRequest("mac-preview", "preview/" + fixture.slug(),
                "MACOS", "X86_64", "preview", "default", draft.id()), "admin");
    }

    @Test
    void channelTargetsCacheValidatorsAndHistoricalAssetsRemainExplicitAndGoverned() throws Exception {
        var fixture = publish();
        String feed = "/updates/" + fixture.slug() + "/windows-stable/latest-mac.yml";
        var initial = get(feed);
        String etag = initial.headers().firstValue("ETag").orElseThrow();
        var draft = updates.create(new SoftwareUpdateCreateRequest(fixture.catalogId(), "2.0.0", "v2.0.0", "MACOS",
                "X86_64", "stable", "default", "ELECTRON", null), "admin");
        var linked = updates.linkPackage(draft.id(), fixture.packageId(), "dist/app+demo.zip", "admin");
        byte[] yaml = ("version: 2.0.0\nfiles:\n - url: app%2Bdemo.zip\n   size: 10\n   sha512: " + linked.sha512Base64() + "\n").getBytes(StandardCharsets.UTF_8);
        updates.uploadAsset(draft.id(), "dist/latest-mac.yml", "METADATA", yaml.length, new ByteArrayInputStream(yaml), "admin");
        updates.publish(draft.id(), "admin");
        assertThat(send(feed, "GET", "If-None-Match", etag).statusCode()).isEqualTo(304);
        updates.saveChannel(fixture.applicationId(), new SoftwareUpdateChannelRequest("windows-stable", "apps/" + fixture.slug(),
                "MACOS", "X86_64", "stable", "default", draft.id()), "admin");
        var switched = send(feed, "GET", "If-None-Match", etag);
        assertThat(switched.statusCode()).isEqualTo(200);
        assertThat(new String(switched.body(), StandardCharsets.UTF_8)).contains("version: 2.0.0");
        assertThat(switched.headers().firstValue("ETag")).isNotEqualTo(initial.headers().firstValue("ETag"));
        String cachedDate = switched.headers().firstValue("Last-Modified").orElse("Tue, 1 Jan 2030 00:00:00 GMT");
        updates.saveChannel(fixture.applicationId(), new SoftwareUpdateChannelRequest("windows-stable", "apps/" + fixture.slug(),
                "MACOS", "X86_64", "stable", "default", fixture.releaseId()), "admin");
        var rollback = send(feed, "GET", "If-Modified-Since", cachedDate);
        assertThat(rollback.statusCode()).isEqualTo(200);
        assertThat(new String(rollback.body(), StandardCharsets.UTF_8)).contains("version: 1.2.3");
        assertThat(rollback.headers().firstValue("Last-Modified")).isEmpty();
        String old = "/releases/" + fixture.slug() + "/" + fixture.releaseId() + "/assets/dist/app%2Bdemo.zip";
        var history = get(old);
        assertThat(history.body()).isEqualTo("0123456789".getBytes(StandardCharsets.UTF_8));
        String originalDate = history.headers().firstValue("Last-Modified").orElseThrow();
        assertThat(send(old, "GET", "If-Modified-Since", originalDate).statusCode()).isEqualTo(304);
        assertThat(send(old, "GET", "Range", "bytes=0-1", "If-Range", originalDate).statusCode()).isEqualTo(206);
        updates.saveChannel(fixture.applicationId(), new SoftwareUpdateChannelRequest("windows-stable", "apps/" + fixture.slug(),
                "MACOS", "X86_64", "stable", "default", null), "admin");
        assertThat(send(feed, "HEAD", "If-None-Match", etag).statusCode()).isEqualTo(404);
        assertThat(get(old).statusCode()).isEqualTo(200);
        updates.setAnonymous(fixture.applicationId(), false, "admin");
        assertThat(send(old, "GET", "Range", "bytes=0-1").statusCode()).isEqualTo(404);
    }

    @Test
    void baizeStableAndPreviewAreSeparateBareManifestsAndHonorPrefixes() throws Exception {
        var fixture = publish();
        var stable = baize(fixture, "stable", "1.2.3");
        baize(fixture, "preview", "2.0.0-beta.1");
        String feed = "/updates/" + fixture.slug() + "/baize-stable/latest.json";
        var response = get(feed);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var document = mapper.readTree(response.body());
        assertThat(document.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(document.has("data")).isFalse();
        assertThat(document.path("version").asText()).isEqualTo("1.2.3");
        assertThat(document.path("package").asText()).isEqualTo(contextPath() + "/releases/" + fixture.slug() + "/" + stable.id() + "/assets/dist/portable%2Bapp.zip");
        assertThat(mapper.readTree(get("/updates/" + fixture.slug() + "/baize-preview/latest.json").body()).path("channel").asText()).isEqualTo("preview");
        assertThat(get("/updates/" + fixture.slug() + "/baize-stable/latest-mac.yml").statusCode()).isEqualTo(404);
        assertThat(get("/updates/" + fixture.slug() + "/windows-stable/preview-mac.yml").statusCode()).isEqualTo(404);
        assertThat(get("/updates/" + fixture.slug() + "/windows-stable/latest.yml").statusCode()).isEqualTo(404);
        var forwarded = mapper.readTree(send(feed, "GET", "X-Forwarded-Prefix", "/hub.v1").body());
        assertThat(forwarded.path("package").asText()).startsWith("/hub.v1/releases/").doesNotContain("/hub.v1/hub.v1/");
        updates.setAnonymous(fixture.applicationId(), false, "admin");
        String etag = response.headers().firstValue("ETag").orElseThrow();
        assertThat(send(feed, "GET", "If-None-Match", etag).statusCode()).isEqualTo(404);
        assertThat(send(feed, "HEAD").statusCode()).isEqualTo(404);
        assertThat(send(feed, "GET", "Range", "bytes=0-1").statusCode()).isEqualTo(404);
    }

    @Test
    void baizeReleaseNotesResolveNestedEncodedNamesAndRemainGoverned() throws Exception {
        var fixture = publish();
        var snapshot = baize(fixture, "stable", "1.2.3", true);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var document = mapper.readTree(get("/updates/" + fixture.slug() + "/baize-stable/latest.json").body());
        String notes = document.path("releasesUrl").asText();
        assertThat(notes).isEqualTo(contextPath() + "/releases/" + fixture.slug() + "/" + snapshot.id()
                + "/assets/dist/nested/notes/release%20notes%2B%E4%B8%AD.html");
        String relative = notes.substring(contextPath().length());
        var response = get(relative);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("<h1>Release notes + 中</h1>".getBytes(StandardCharsets.UTF_8));
        var original = mapper.readTree(get("/releases/" + fixture.slug() + "/" + snapshot.id()
                + "/assets/dist/nested/latest.json").body());
        assertThat(original.path("releasesUrl").asText()).isEqualTo("notes/release%20notes%2B%E4%B8%AD.html");
        updates.setAnonymous(fixture.applicationId(), false, "admin");
        assertThat(get(relative).statusCode()).isEqualTo(404);
    }

    SoftwareUpdateReleaseResponse baize(Fixture fixture, String channel, String version) {
        return baize(fixture, channel, version, false);
    }

    SoftwareUpdateReleaseResponse baize(Fixture fixture, String channel, String version, boolean withNotes) {
        byte[] bytes = ("portable-" + channel + "-" + version).getBytes(StandardCharsets.UTF_8);
        var upload = uploads.createSession(new SoftwareUploadSessionCreateRequest(fixture.catalogId(), "portable.zip", "ZIP",
                "WINDOWS", "X86_64", null, null, version, (long) bytes.length), "admin");
        uploads.putChunk(upload.id(), 1, (long) bytes.length, new ByteArrayInputStream(bytes), "admin");
        long pkg = uploads.complete(upload.id(), "admin").packageRevisionId();
        packages.confirm(pkg, "admin"); packages.publish(pkg);
        var draft = updates.create(new SoftwareUpdateCreateRequest(fixture.catalogId(), version, "v" + version,
                "WINDOWS", "X86_64", channel, "portable", "BAIZE", "0.70.0"), "admin");
        String parent = withNotes ? "dist/nested/" : "dist/";
        var installer = updates.linkPackage(draft.id(), pkg, parent + "portable+app.zip", "admin");
        if (withNotes) {
            byte[] notes = "<h1>Release notes + 中</h1>".getBytes(StandardCharsets.UTF_8);
            updates.uploadAsset(draft.id(), parent + "notes/release notes+中.html", "OTHER", notes.length,
                    new ByteArrayInputStream(notes), "admin");
        }
        byte[] json = ("{\"schemaVersion\":1,\"version\":\"" + version + "\",\"piVersion\":\"0.70.0\",\"platform\":\"win32\",\"arch\":\"x64\","
                + "\"channel\":\"" + channel + "\",\"publishedAt\":\"2026-10-15T08:00:00Z\",\"package\":\"portable%2Bapp.zip\","
                + "\"sha256\":\"" + installer.sha256() + "\",\"bytes\":" + installer.byteSize()
                + (withNotes ? ",\"releasesUrl\":\"notes/release%20notes%2B%E4%B8%AD.html\"" : "") + "}").getBytes(StandardCharsets.UTF_8);
        updates.uploadAsset(draft.id(), parent + "latest.json", "METADATA", json.length, new ByteArrayInputStream(json), "admin");
        var published = updates.publish(draft.id(), "admin");
        updates.saveChannel(fixture.applicationId(), new SoftwareUpdateChannelRequest("baize-" + channel,
                "baize/" + fixture.slug() + "-" + channel, "WINDOWS", "X86_64", channel, "portable", published.id()), "admin");
        return published;
    }
}
