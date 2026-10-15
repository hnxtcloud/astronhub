package com.iflytek.skillhub.service.plugin;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.compress.archivers.tar.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/** Opt-in real no-uplink registry acceptance. Never uses user npm settings or public registries. */
@EnabledIfSystemProperty(named = "astronhub.npm.integration", matches = "true")
class NpmRegistryActualIntegrationTest {
    @Test
    void realRegistryPublishesOriginalScopedTgzReusesExactBytesAndRefusesConflicts()
            throws Exception {
        String registry = System.getProperty("astronhub.npm.registry");
        URI uri = URI.create(registry);
        assertThat(uri.getHost()).isEqualTo("127.0.0.1");
        var mapper = new ObjectMapper();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String user = "fixture" + suffix;
        var account =
                mapper.createObjectNode()
                        .put("name", user)
                        .put("password", UUID.randomUUID().toString())
                        .put("email", user + "@example.invalid")
                        .put("type", "user");
        var response =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        registry
                                                                + "-/user/org.couchdb.user:"
                                                                + user))
                                        .header("Content-Type", "application/json")
                                        .PUT(
                                                HttpRequest.BodyPublishers.ofString(
                                                        mapper.writeValueAsString(account)))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 201);
        String token = mapper.readTree(response.body()).path("token").asText();
        assertThat(token).isNotBlank();
        String name = "@astronhub/fixture-" + suffix;
        String manifest =
                "{\"name\":\""
                        + name
                        + "\",\"version\":\"1.0.0\",\"publishConfig\":{\"registry\":\"https://registry.npmjs.org\"},\"scripts\":{\"prepublishOnly\":\"exit"
                        + " 87\",\"postinstall\":\"exit 88\"}}";
        Path archive = Files.createTempFile("npm-original-", ".tgz");
        try {
            tgz(archive, manifest, "original bytes");
            byte[] original = Files.readAllBytes(archive);
            var publisher = new NpmRegistryPublisher(mapper);
            var target = new NpmRegistryPublisher.Target(1L, registry, "fixture-only");
            assertThat(publisher.publish(archive, manifest, target, token).reused()).isFalse();
            assertThat(Files.readAllBytes(archive)).isEqualTo(original);
            assertThat(publisher.publish(archive, manifest, target, token).reused()).isTrue();
            tgz(archive, manifest, "different bytes");
            assertThatThrownBy(() -> publisher.publish(archive, manifest, target, token))
                    .isInstanceOf(NpmRegistryPublisher.Failure.class)
                    .hasMessage("REGISTRY_VERSION_CONFLICT");
            String otherManifest = manifest.replace("1.0.0", "2.0.0");
            tgz(archive, otherManifest, "new version");
            assertThat(publisher.publish(archive, otherManifest, target, token).reused()).isFalse();
            Files.write(archive, original);
            assertThat(publisher.publish(archive, manifest, target, token).reused()).isTrue();
            otherManifest = manifest.replace("1.0.0", "3.0.0");
            tgz(archive, otherManifest, "unauthorized version");

            assertThatThrownBy(
                            () ->
                                    publisher.publish(
                                            archive,
                                            manifest.replace("1.0.0", "3.0.0"),
                                            target,
                                            "invalid-disposable-token"))
                    .isInstanceOf(NpmRegistryPublisher.Failure.class)
                    .hasMessage("REGISTRY_AUTHENTICATION");
            var dependencies =
                    publisher.dependencies(
                            "{\"dependencies\":{\""
                                    + name
                                    + "\":\"1.0.0\",\"missing-"
                                    + suffix
                                    + "\":\"1.0.0\"}}",
                            target,
                            token);
            assertThat(dependencies)
                    .extracting(NpmRegistryPublisher.Dependency::availability)
                    .containsExactly("AVAILABLE", "MISSING");
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    static void tgz(Path path, String manifest, String payload) throws Exception {
        try (var gzip = new GZIPOutputStream(Files.newOutputStream(path));
                var tar = new TarArchiveOutputStream(gzip)) {
            for (var file :
                    Map.of("package/package.json", manifest, "package/index.js", payload)
                            .entrySet()) {
                byte[] content = file.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry(file.getKey());
                entry.setSize(content.length);
                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
            }
        }
    }
}
