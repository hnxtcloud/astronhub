package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.software.*;
import com.iflytek.skillhub.storage.StorageProperties;
import com.iflytek.skillhub.storage.software.LocalSoftwareTransferStorage;
import com.iflytek.skillhub.storage.software.SoftwareTransferStorageProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Native metadata contract on real provider bytes, independent of database/HTTP details. */
class SoftwareUpdateMetadataValidatorTest {
    @TempDir Path temp;

    @Test
    void originalMacMetadataMayContainZipDmgAndEmbeddedBlockmapSize() {
        assertThat(SoftwarePackageFormat.valueOf("DMG").isAutoInspected()).isFalse();
        var properties = new StorageProperties();
        properties.getLocal().setBasePath(temp.resolve("base").toString());
        var transfer = new SoftwareTransferStorageProperties();
        transfer.setTempDir(temp.resolve("staging").toString());
        var storage = new LocalSoftwareTransferStorage(properties, transfer);
        String sha = "3a81oZNherrMQXNJriBBMRLm+k6JqX6iCp7u5ktV05ohkpkqJ0/BqDa6PCOj/uu9RU1EI2Q86A4qmslPpUyknw==";
        byte[] yaml = ("version: 1.0.0\nfiles:\n  - url: app%20name.zip\n    size: 100\n    sha512: " + sha
                + "\n    blockMapSize: 20\n  - url: app.dmg\n    size: 100\n    sha512: " + sha + "\n")
                .getBytes(StandardCharsets.UTF_8);
        String ref = storage.initiateUpload("native.yml");
        storage.stageChunk(ref, 1, new ByteArrayInputStream(yaml), yaml.length);
        storage.commitChunk(ref, "native.yml", 1);
        storage.completeUpload(ref, "native.yml", List.of(1));
        var snapshot = new SoftwareUpdateRelease(1L, 1L, "1.0.0", "v1.0.0", "MACOS", "ARM64", "stable", "default", "ELECTRON", null, null, "admin");
        var metadata = new SoftwareReleaseAsset(1L, "dist/latest-mac.yml", "METADATA", null, "native.yml", yaml.length, "hash", sha);
        var zip = new SoftwareReleaseAsset(1L, "dist/app name.zip", "INSTALLER", 2L, "zip", 100, "hash", sha);
        var dmg = new SoftwareReleaseAsset(1L, "dist/app.dmg", "INSTALLER", 3L, "dmg", 100, "hash", sha);
        var validator = new SoftwareUpdateMetadataValidator(storage, new ObjectMapper());
        assertThatCode(() -> validator.validate(snapshot, List.of(metadata, zip, dmg)))
                .doesNotThrowAnyException();
        byte[] dmgOnly = ("version: 1.0.0\nfiles:\n - url: app.dmg\n   size: 100\n   sha512: " + sha + "\n").getBytes(StandardCharsets.UTF_8);
        String dmgRef = storage.initiateUpload("dmg-only.yml");
        storage.stageChunk(dmgRef, 1, new ByteArrayInputStream(dmgOnly), dmgOnly.length);
        storage.commitChunk(dmgRef, "dmg-only.yml", 1);
        storage.completeUpload(dmgRef, "dmg-only.yml", List.of(1));
        var dmgMetadata = new SoftwareReleaseAsset(1L, "dist/latest-mac.yml", "METADATA", null, "dmg-only.yml", dmgOnly.length, "hash", sha);
        assertThatThrownBy(() -> validator.validate(snapshot, List.of(dmgMetadata, dmg)))
                .hasMessageContaining("error.software.update.metadata");
        byte[] checksumBytes = ("0".repeat(64) + "  app%20name.zip\n").getBytes(StandardCharsets.UTF_8);
        String checksumRef = storage.initiateUpload("checksums");
        storage.stageChunk(checksumRef, 1, new ByteArrayInputStream(checksumBytes), checksumBytes.length);
        storage.commitChunk(checksumRef, "checksums", 1);
        storage.completeUpload(checksumRef, "checksums", List.of(1));
        var checksum = new SoftwareReleaseAsset(1L, "dist/SHA256SUMS", "CHECKSUM", null, "checksums", checksumBytes.length, "hash", sha);
        assertThatThrownBy(() -> validator.validate(snapshot, List.of(metadata, zip, dmg, checksum)))
                .hasMessageContaining("error.software.update.metadata");
    }
}
