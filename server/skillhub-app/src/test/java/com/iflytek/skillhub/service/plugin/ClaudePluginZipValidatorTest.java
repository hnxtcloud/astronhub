package com.iflytek.skillhub.service.plugin;


import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

class ClaudePluginZipValidatorTest {
    @Test
    void acceptsMultipleSkillsWithoutHooksAgentsOrMcp() throws Exception {
        var archive = zip("skills/review/SKILL.md", "skills/testing/SKILL.md");
        var validated = new ClaudePluginZipValidator(1000000, 2000000, 100).validate(archive, "1.0.0");
        assertThat(validated.inventoryJson()).contains("skills/review/SKILL.md", "skills/testing/SKILL.md");
    }

    @ParameterizedTest
    @MethodSource("ambiguousArchives")
    void rejectsAmbiguousNativeExtractionPaths(byte[] archive) {
        assertThatThrownBy(() -> new ClaudePluginZipValidator(1000000, 2000000, 100)
                .validate(archive, "1.0.0"))
                .isInstanceOf(DomainBadRequestException.class);
    }

    static java.util.stream.Stream<byte[]> ambiguousArchives() throws Exception {
        byte[] alias = "../outside.md".getBytes(StandardCharsets.UTF_8);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update("commands/hello.md".getBytes(StandardCharsets.UTF_8));
        byte[] extra = java.nio.ByteBuffer.allocate(alias.length + 9).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0x7075).putShort((short) (alias.length + 5)).put((byte) 1)
                .putInt((int) crc.getValue()).put(alias).array();
        return java.util.stream.Stream.of(zip(".claude-plugin"), zipWithExtras(extra, "commands/hello.md"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/absolute.md", "C:/drive.md", "commands/../outside.md", "commands\\outside.md", "commands/CON.txt"})
    void refusesPathsThatCannotBeSafelyExtractedOnSupportedHosts(String path) throws Exception {
        assertThatThrownBy(() -> new ClaudePluginZipValidator(1000000, 2000000, 100).validate(zip(path), "1.0.0"))
                .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void refusesSymlinkModesDuplicatePathsAndConfiguredBudgetOverruns() throws Exception {
        ClaudePluginZipValidator validator = new ClaudePluginZipValidator(1000000, 2000000, 100);
        byte[] linked = zip("commands/hello.md");
        for (int index = 0; index + 46 < linked.length; index++) {
            if (linked[index] == 0x50 && linked[index + 1] == 0x4b && linked[index + 2] == 1 && linked[index + 3] == 2) {
                int mode = 0120777; linked[index + 40] = (byte) mode; linked[index + 41] = (byte) (mode >>> 8); break;
            }
        }
        assertThatThrownBy(() -> validator.validate(linked, "1.0.0")).isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> validator.validate(zip("commands/hello.md", "commands/HELLO.md"), "1.0.0"))
                .isInstanceOf(DomainBadRequestException.class);
        byte[] original = zip("commands/hello.md");
        assertThatThrownBy(() -> new ClaudePluginZipValidator(1000000, 10, 100).validate(original, "1.0.0"))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> new ClaudePluginZipValidator(1000000, 2000000, 1).validate(original, "1.0.0"))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> new ClaudePluginZipValidator(20, 2000000, 100).readBounded(new java.io.ByteArrayInputStream(original)))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> validator.validate(original, "2.0.0")).isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void validatesActualClaudeArchiveAndRejectsUnsafePathsBeforeProducingEvidence() throws Exception {
        ClaudePluginZipValidator validator = new ClaudePluginZipValidator(1000000, 2000000, 100);
        var result = validator.validate(zip("commands/hello.md"), "1.0.0");
        assertThat(result.nativeName()).isEqualTo("sample");
        assertThat(result.nativeVersion()).isEqualTo("1.0.0");
        assertThat(result.inventoryJson()).contains("commands/hello.md");
        assertThat(result.sha256()).matches("[a-f0-9]{64}");
        assertThatThrownBy(() -> validator.validate(zip("../outside.sh"), "1.0.0"))
                .isInstanceOf(DomainBadRequestException.class);
    }

    static byte[] zip(String... resources) throws Exception {
        return zipWithExtras(null, resources);
    }
    static byte[] zipWithExtras(byte[] extra, String... resources) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(".claude-plugin/plugin.json"));
            zip.write("{\"name\":\"sample\",\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String resource : resources) {
                ZipEntry entry = new ZipEntry(resource);
                if (extra != null) entry.setExtra(extra);
                zip.putNextEntry(entry);
                zip.write("# Hello".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}

