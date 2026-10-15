package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.software.SoftwareReleaseAsset;
import com.iflytek.skillhub.domain.software.SoftwareUpdateRelease;
import com.iflytek.skillhub.storage.StorageProperties;
import com.iflytek.skillhub.storage.software.LocalSoftwareTransferStorage;
import com.iflytek.skillhub.storage.software.SoftwareTransferStorageProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Native client requirements at the bounded original-metadata validation boundary. */
class SoftwareUpdateNativeManifestContractTest {
    @TempDir Path temp;
    private static final String SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String SHA512 = "3a81oZNherrMQXNJriBBMRLm+k6JqX6iCp7u5ktV05ohkpkqJ0/BqDa6PCOj/uu9RU1EI2Q86A4qmslPpUyknw==";
    private static final String MANIFEST = """
            {"schemaVersion":1,"version":"1.2.3","piVersion":"0.70.0","platform":"win32","arch":"x64",
             "channel":"stable","publishedAt":"2026-10-14T08:00:00Z","package":"app.zip",
             "sha256":"%s","bytes":3,"releasesUrl":"releases"}
            """.formatted(SHA256);

    @Test
    void baizeRequiresAParseablePublicationInstantAndLocalOptionalReleaseLink() {
        assertThatCode(() -> validate("BAIZE", MANIFEST)).doesNotThrowAnyException();
        for (String invalid : List.of(MANIFEST.replace("2026-10-14T08:00:00Z", "yesterday"),
                MANIFEST.replace("\"releases\"", "\"https://public.example/releases\""))) {
            assertThatThrownBy(() -> validate("BAIZE", invalid)).isInstanceOf(DomainBadRequestException.class)
                    .hasMessageContaining("error.software.update.metadata");
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "null", "[]", "1"})
    void nonObjectMetadataReturnsAnActionableValidationError(String text) {
        assertThatThrownBy(() -> validate("BAIZE", text)).isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.software.update.metadata");
    }

    @Test
    void electronCannotPublishAnUncheckedLegacyPath() {
        String yaml = "version: 1.2.3\nfiles:\n - url: app.zip\n   size: 3\n   sha512: " + SHA512 + "\n";
        assertThatCode(() -> validate("ELECTRON", yaml)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validate("ELECTRON", yaml + "path: notes.txt\n"))
                .isInstanceOf(DomainBadRequestException.class).hasMessageContaining("error.software.update.metadata");
    }

    @Test
    void electronRejectsUnsupportedWebInstallerPackageReferences() {
        String yaml = "version: 1.2.3\nfiles:\n - url: app.zip\n   size: 3\n   sha512: " + SHA512 + "\n";
        assertThatThrownBy(() -> validate("ELECTRON", yaml + "packages:\n  x64:\n    path: unchecked.7z\n"))
                .isInstanceOf(DomainBadRequestException.class).hasMessageContaining("error.software.update.metadata");
    }

    private void validate(String protocol, String text) {
        var properties = new StorageProperties();
        properties.getLocal().setBasePath(temp.resolve("base").toString());
        var transfer = new SoftwareTransferStorageProperties();
        transfer.setTempDir(temp.resolve("staging").toString());
        var storage = new LocalSoftwareTransferStorage(properties, transfer);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String key = "metadata/" + UUID.randomUUID();
        String ref = storage.initiateUpload(key);
        storage.stageChunk(ref, 1, new ByteArrayInputStream(bytes), bytes.length);
        storage.commitChunk(ref, key, 1);
        storage.completeUpload(ref, key, List.of(1));
        var snapshot = new SoftwareUpdateRelease(1L, 1L, "1.2.3", "v1.2.3", "WINDOWS", "X86_64", "stable", "default", protocol, "BAIZE".equals(protocol) ? "0.70.0" : null, null, "admin");
        var metadata = new SoftwareReleaseAsset(1L, "latest." + ("BAIZE".equals(protocol) ? "json" : "yml"), "METADATA", null, key, bytes.length, SHA256, SHA512);
        var installer = new SoftwareReleaseAsset(1L, "app.zip", "INSTALLER", 2L, "installer", 3, SHA256, SHA512);
        var notes = new SoftwareReleaseAsset(1L, "notes.txt", "OTHER", null, "notes", 3, SHA256, SHA512);
        new SoftwareUpdateMetadataValidator(storage, new ObjectMapper()).validate(snapshot, List.of(metadata, installer, notes));
    }
}
