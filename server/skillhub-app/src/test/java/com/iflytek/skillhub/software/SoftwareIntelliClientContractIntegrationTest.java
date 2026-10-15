package com.iflytek.skillhub.software;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.dto.software.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Original Intelli detection and proxy code plus installed updater against actual Tomcat bytes. */
class SoftwareIntelliClientContractIntegrationTest extends SoftwareGitLabUpdateReadIntegrationTest {
    @Test
    @EnabledIfSystemProperty(named = "astronhub.update.intelliProbe", matches = "true")
    void originalIntelliTypeScriptAndInstalledUpdaterUseTheLiveGitLabContract() throws Exception {
        var fixture = publish(); // Existing fixture seeds policy/storage and creates an isolated app.
        byte[] binary;
        try (var buffer = new ByteArrayOutputStream()) {
            try (var zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
                var entry = new ZipEntry("README.txt");
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write("Inert synthetic Intelli contract fixture. No executable or app installation.\n"
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            binary = buffer.toByteArray();
        }
        String filename = "intelli-probe+1.2.4-mac.zip";
        var session = uploads.createSession(new SoftwareUploadSessionCreateRequest(
                fixture.catalogId(), "intelli-probe.zip", "ZIP", "MACOS", "X86_64",
                null, null, "1.2.4", (long) binary.length), "admin");
        uploads.putChunk(session.id(), 1, binary.length, new ByteArrayInputStream(binary), "admin");
        long packageId = uploads.complete(session.id(), "admin").packageRevisionId();
        packages.confirm(packageId, "admin");
        packages.publish(packageId);
        var snapshot = updates.create(new SoftwareUpdateCreateRequest(
                fixture.catalogId(), "1.2.4", "v1.2.4", "MACOS", "X86_64", "stable",
                "default", "ELECTRON", null), "admin");
        var linked = updates.linkPackage(snapshot.id(), packageId, "dist/" + filename, "admin");
        // Create a NEW original canonical metadata asset; do not rename or project old latest.yml.
        byte[] yaml = ("version: 1.2.4\nfiles:\n - url: intelli-probe%2B1.2.4-mac.zip\n   size: "
                + binary.length + "\n   sha512: " + linked.sha512Base64() + "\n")
                .getBytes(StandardCharsets.UTF_8);
        updates.uploadAsset(snapshot.id(), "dist/latest-mac.yml", "METADATA", yaml.length,
                new ByteArrayInputStream(yaml), "admin");
        updates.publish(snapshot.id(), "admin");
        // Reuse this isolated fixture's existing channel key; bind the native compatibility project.
        updates.saveChannel(fixture.applicationId(), new SoftwareUpdateChannelRequest(
                "windows-stable", "compat/" + fixture.slug(), "MACOS", "X86_64", "stable",
                "default", snapshot.id()), "admin");
        Path parent = Path.of("target/update-contract").toAbsolutePath().normalize();
        Files.createDirectories(parent);
        Path run = Files.createTempDirectory(parent, "intelli-gitlab-");
        Path expected = run.resolve("expected.zip");
        Files.write(expected, binary);
        Path manifest = run.resolve("fixture.json");
        new ObjectMapper().writeValue(manifest.toFile(), Map.of(
                "baseUrl", "http://127.0.0.1:" + port + contextPath(),
                "owner", "compat", "repo", fixture.slug(), "artifactPath", "dist",
                "expectedVersion", "1.2.4", "expectedTag", "v1.2.4", "expectedNotes", "Original notes",
                "expectedAssetFile", expected.toString(), "assetFilename", filename));
        Path script = Path.of(System.getProperty("astronhub.update.intelliProbeScript",
                "../../scripts/tests/intelli-gitlab-contract-probe.cjs")).toAbsolutePath().normalize();
        assertThat(Files.isRegularFile(script)).isTrue();
        Path output = run.resolve("probe-output");
        var command = new ArrayList<>(List.of(System.getProperty("astronhub.update.node", "node"),
                script.toString(), "--fixture", manifest.toString(), "--out", output.toString()));
        String clientRoot = System.getProperty("astronhub.update.intelliClientRoot");
        assertThat(clientRoot).as("Explicit astronhub.update.intelliClientRoot is required").isNotBlank();
        command.addAll(List.of("--client-root", clientRoot));
        var builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(run.resolve("probe.log").toFile());
        Set<String> strip = Set.of("NODE_OPTIONS", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NODE_USE_ENV_PROXY");
        builder.environment().keySet().removeIf(key -> strip.contains(key.toUpperCase(Locale.ROOT)));
        builder.environment().put("NO_PROXY", "127.0.0.1,::1");
        Process process = builder.start();
        try {
            assertThat(process.waitFor(150, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).as("Inspect sanitized target/update-contract probe.log on failure").isZero();
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
        Path report;
        try (var entries = Files.list(output)) {
            report = entries.filter(Files::isDirectory).map(value -> value.resolve("report.json"))
                    .filter(Files::isRegularFile).findFirst().orElseThrow();
        }
        var evidence = new ObjectMapper().readTree(report.toFile());
        assertThat(evidence.path("ok").asBoolean()).isTrue();
        assertThat(evidence.path("mode").asText()).isEqualTo("live-spring-contract");
        assertThat(evidence.path("proxyStopped").asBoolean()).isTrue();
        assertThat(evidence.path("installation").asBoolean()).isFalse();
        assertThat(evidence.path("applicationStarted").asBoolean()).isFalse();
        assertThat(evidence.path("differentialUpdateTested").asBoolean()).isFalse();
    }
}
