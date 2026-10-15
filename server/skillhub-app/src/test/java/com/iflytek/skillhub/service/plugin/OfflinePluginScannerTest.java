package com.iflytek.skillhub.service.plugin;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

class OfflinePluginScannerTest {
    @TempDir Path temp;

    @Test
    void repositoryPreviewIsolatesInvalidCandidateAndDiscoversNativeResourcesWithoutExecution()
            throws Exception {
        Path source = temp.resolve("repo.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(source))) {
            entry(
                    zip,
                    "repo/a/.claude-plugin/plugin.json",
                    "{\"name\":\"alpha\",\"version\":\"1.0.0\"}");
            entry(zip, "repo/a/skills/hello/SKILL.md", "Hello");
            entry(
                    zip,
                    "repo/b/.codex-plugin/plugin.json",
                    "{\"name\":\"beta\",\"version\":\"2.0.0\",\"skills\":\"./skills\"}");
            entry(zip, "repo/b/skills/world/SKILL.md", "world");
            entry(zip, "repo/broken/.claude-plugin/plugin.json", "{broken");
        }
        try (var scan =
                new OfflinePluginScanner().scan(source, "ZIP", PluginImportLimits.defaults())) {
            assertThat(scan.candidates()).hasSize(3);
            assertThat(scan.candidates())
                    .filteredOn(c -> c.error() == null)
                    .extracting(c -> c.runtime())
                    .containsExactly("claude-code", "codex");
            assertThat(scan.candidates().getFirst().inventoryJson())
                    .contains("SKILL.md", "runtimeTest", "UNKNOWN");
            assertThat(scan.candidates().getLast().error()).isNotBlank();
        }
    }

    @Test
    void rejectsHiddenTarEntriesAfterEndOfArchiveInsteadOfPreservingAmbiguousNativeBytes()
            throws Exception {
        Path source = temp.resolve("hidden.tgz");
        try (var gzip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(source))) {
            gzip.write(
                    tar(
                            "package/package.json",
                            "{\"name\":\"demo\",\"version\":\"1.0.0\",\"pi\":{}}"));
            gzip.write(tar("package/evil.ts", "hidden"));
        }
        assertThatThrownBy(
                        () ->
                                new OfflinePluginScanner()
                                        .scan(source, "TGZ", PluginImportLimits.defaults()))
                .isInstanceOf(
                        com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException
                                .class);
    }

    @Test
    void archiveSafetyRejectsTraversalLinksCaseAliasesAndGlobalExpandedBudget() throws Exception {
        for (String bad :
                java.util.List.of("../escape", "a/../../escape", "C:/escape", "a\\escape")) {
            Path source = temp.resolve(java.util.UUID.randomUUID() + ".zip");
            try (var zip = new ZipOutputStream(Files.newOutputStream(source))) {
                entry(zip, bad, "payload");
            }
            assertThatThrownBy(
                            () ->
                                    new OfflinePluginScanner()
                                            .scan(source, "ZIP", PluginImportLimits.defaults()))
                    .as(bad)
                    .isInstanceOf(
                            com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException
                                    .class);
        }
        Path linked = temp.resolve("link.zip");
        try (var zip =
                new org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(linked)) {
            var entry = new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("link");
            entry.setUnixMode(0120777);
            zip.putArchiveEntry(entry);
            zip.write("../outside".getBytes());
            zip.closeArchiveEntry();
        }
        assertThatThrownBy(
                        () ->
                                new OfflinePluginScanner()
                                        .scan(linked, "ZIP", PluginImportLimits.defaults()))
                .isInstanceOf(
                        com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException
                                .class);
        Path aliases = temp.resolve("alias.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(aliases))) {
            entry(zip, "A/file", "one");
            entry(zip, "a/FILE", "two");
        }
        assertThatThrownBy(
                        () ->
                                new OfflinePluginScanner()
                                        .scan(aliases, "ZIP", PluginImportLimits.defaults()))
                .isInstanceOf(
                        com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException
                                .class);
        Path large = temp.resolve("large.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(large))) {
            entry(zip, "first", "123456");
            entry(zip, "second", "123456");
        }
        assertThatThrownBy(
                        () ->
                                new OfflinePluginScanner()
                                        .scan(
                                                large,
                                                "ZIP",
                                                new PluginImportLimits(100000, 100000, 10, 20, 10)))
                .isInstanceOf(
                        com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException
                                .class);
    }

    @Test
    void standaloneNativeZipIsRetainedWithoutRepackingOrLosingExecutableMode() throws Exception {
        Path source = temp.resolve("native.zip");
        try (var zip =
                new org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(source)) {
            zip.setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode.Never);
            var manifest =
                    new org.apache.commons.compress.archivers.zip.ZipArchiveEntry(
                            ".claude-plugin/plugin.json");
            manifest.setUnixMode(0100644);
            zip.putArchiveEntry(manifest);
            zip.write("{\"name\":\"native\",\"version\":\"1.0.0\"}".getBytes());
            zip.closeArchiveEntry();
            var script =
                    new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("bin/run.sh");
            script.setUnixMode(0100755);
            zip.putArchiveEntry(script);
            zip.write("#!/bin/sh\necho native".getBytes());
            zip.closeArchiveEntry();
        }
        try (var scan =
                new OfflinePluginScanner().scan(source, "ZIP", PluginImportLimits.defaults())) {
            assertThat(Files.readAllBytes(scan.candidates().getFirst().artifact()))
                    .isEqualTo(Files.readAllBytes(source));
            assertThat(scan.candidates().getFirst().inventoryJson())
                    .contains("ORIGINAL_NATIVE_ZIP");
        }
    }

    @Test
    void portableCodexOpenaiOverlayReferencesMustStayInsideTheNativePackage() throws Exception {
        Path source = temp.resolve("portable.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(source))) {
            entry(
                    zip,
                    "plugin.json",
                    "{\"$schema\":\"https://agent-plugins.org/schemas/1.0.0/plugin.schema.json\",\"name\":\"portable\",\"version\":\"1.0.0\",\"extensions\":{\"com.openai\":{\"apps\":\"../outside.json\"}}}");
        }
        try (var scan =
                new OfflinePluginScanner().scan(source, "ZIP", PluginImportLimits.defaults())) {
            assertThat(scan.candidates().getFirst().status()).isEqualTo("INVALID");
        }
    }

    @Test
    void portableCodexFixtureValidatesOpenaiOverlayAndRejectsMalformedNativeVersions()
            throws Exception {
        Path source = temp.resolve("portable-valid.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(source))) {
            entry(
                    zip,
                    "plugin.json",
                    "{\"$schema\":\"https://agent-plugins.org/schemas/1.0.0/plugin.schema.json\",\"name\":\"portable\",\"version\":\"1.0.0+build-01\",\"extensions\":{\"com.openai\":{\"apps\":\"./.app.json\",\"hooks\":\"./hooks/hooks.json\",\"onboardingSkill\":\"./skills/setup/SKILL.md\",\"interface\":{\"composerIcon\":\"./assets/icon.svg\",\"screenshots\":[\"./assets/icon.svg\"]}}}}");
            entry(zip, ".app.json", "{}");
            entry(zip, "hooks/hooks.json", "{}");
            entry(zip, "skills/setup/SKILL.md", "Setup");
            entry(zip, "assets/icon.svg", "<svg />");
        }
        try (var scan =
                new OfflinePluginScanner().scan(source, "ZIP", PluginImportLimits.defaults())) {
            assertThat(scan.candidates().getFirst().status()).isEqualTo("VALID");
            assertThat(scan.candidates().getFirst().inventoryJson())
                    .contains("onboardingSkill", "interface.composerIcon", "ORIGINAL_NATIVE_ZIP");
        }
        for (String version :
                java.util.List.of("1.0.0-..", "1.0.0-01", "1.0.0-", "1.0.0+", "01.0.0")) {
            Path invalid = temp.resolve(java.util.UUID.randomUUID() + ".zip");
            try (var zip = new ZipOutputStream(Files.newOutputStream(invalid))) {
                entry(
                        zip,
                        ".claude-plugin/plugin.json",
                        "{\"name\":\"bad\",\"version\":\"" + version + "\"}");
            }
            try (var scan =
                    new OfflinePluginScanner()
                            .scan(invalid, "ZIP", PluginImportLimits.defaults())) {
                assertThat(scan.candidates().getFirst().status()).as(version).isEqualTo("INVALID");
            }
        }
    }

    static byte[] tar(String name, String text) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var tar =
                new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(bytes)) {
            byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var entry = new org.apache.commons.compress.archivers.tar.TarArchiveEntry(name);
            entry.setSize(data.length);
            tar.putArchiveEntry(entry);
            tar.write(data);
            tar.closeArchiveEntry();
        }
        return bytes.toByteArray();
    }

    static void entry(ZipOutputStream zip, String path, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
