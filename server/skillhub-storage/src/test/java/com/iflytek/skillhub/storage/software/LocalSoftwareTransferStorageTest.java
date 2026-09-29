package com.iflytek.skillhub.storage.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.storage.StorageAccessException;
import com.iflytek.skillhub.storage.StorageProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Byte-true tests for the local chunked transfer implementation: real files,
 * real digests, bounded range reads.
 */
class LocalSoftwareTransferStorageTest {

    @TempDir
    Path tempDir;

    private LocalSoftwareTransferStorage newStorage(Path basePath, Path tempRoot) {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.getLocal().setBasePath(basePath.toString());
        SoftwareTransferStorageProperties transferProperties = new SoftwareTransferStorageProperties();
        transferProperties.setTempDir(tempRoot.toString());
        return new LocalSoftwareTransferStorage(storageProperties, transferProperties);
    }

    @Test
    void completeMerge_producesExactBytesAndDigest() throws Exception {
        LocalSoftwareTransferStorage storage = newStorage(tempDir.resolve("base"), tempDir.resolve("tmp"));
        String objectKey = "software/packages/abc/demo.msi";
        String sessionRef = storage.initiateUpload(objectKey);

        byte[] first = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        byte[] second = "SECOND-CHUNK-BYTES".getBytes(StandardCharsets.UTF_8);

        SoftwareTransferStorage.StagedChunk staged1 = storage.stageChunk(
                sessionRef, 1, new ByteArrayInputStream(first), first.length);
        storage.commitChunk(sessionRef, objectKey, 1);
        SoftwareTransferStorage.StagedChunk staged2 = storage.stageChunk(
                sessionRef, 2, new ByteArrayInputStream(second), second.length);
        storage.commitChunk(sessionRef, objectKey, 2);

        assertThat(staged1.sha256Hex()).isEqualTo(sha256Hex(first));
        assertThat(staged2.sha256Hex()).isEqualTo(sha256Hex(second));

        SoftwareTransferStorage.CompleteResult result = storage.completeUpload(
                sessionRef, objectKey, List.of(1, 2));

        byte[] expected = concat(first, second);
        assertThat(result.byteSize()).isEqualTo(expected.length);
        assertThat(result.sha256Hex()).isEqualTo(sha256Hex(expected));
        assertThat(Files.readAllBytes(tempDir.resolve("base").resolve(objectKey)))
                .isEqualTo(expected);
        // session temp dir is reclaimed after a successful merge
        assertThat(Files.exists(tempDir.resolve("tmp").resolve(sessionRef))).isFalse();
    }

    @Test
    void conflictingRetransmit_discardsStagedBytesAndKeepsCommittedPart() throws Exception {
        LocalSoftwareTransferStorage storage = newStorage(tempDir.resolve("base"), tempDir.resolve("tmp"));
        String sessionRef = storage.initiateUpload("software/packages/abc/app.exe");

        byte[] original = "original-bytes".getBytes(StandardCharsets.UTF_8);
        storage.stageChunk(sessionRef, 1, new ByteArrayInputStream(original), original.length);
        storage.commitChunk(sessionRef, "software/packages/abc/app.exe", 1);

        byte[] conflicting = "CONFLICTING".getBytes(StandardCharsets.UTF_8);
        SoftwareTransferStorage.StagedChunk staged =
                storage.stageChunk(sessionRef, 1, new ByteArrayInputStream(conflicting), conflicting.length);
        // business layer rejects the digest, storage only discards the staging
        storage.discardChunk(sessionRef, 1);

        assertThat(staged.sha256Hex()).isEqualTo(sha256Hex(conflicting));
        // committed part untouched
        SoftwareTransferStorage.CompleteResult result = storage.completeUpload(
                sessionRef, "software/packages/abc/app.exe", List.of(1));
        assertThat(result.byteSize()).isEqualTo(original.length);
        assertThat(result.sha256Hex()).isEqualTo(sha256Hex(original));
    }

    @Test
    void stageChunk_withWrongLength_failsAndLeavesNoStaging() throws Exception {
        LocalSoftwareTransferStorage storage = newStorage(tempDir.resolve("base"), tempDir.resolve("tmp"));
        String sessionRef = storage.initiateUpload("software/packages/abc/app.deb");

        byte[] bytes = "12345".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> storage.stageChunk(
                sessionRef, 1, new ByteArrayInputStream(bytes), bytes.length + 10))
                .isInstanceOf(StorageAccessException.class);
        assertThat(Files.exists(tempDir.resolve("tmp").resolve(sessionRef).resolve("part-1.incoming")))
                .isFalse();
    }

    @Test
    void readRange_returnsExactlyTheRequestedSlice() throws Exception {
        LocalSoftwareTransferStorage storage = newStorage(tempDir.resolve("base"), tempDir.resolve("tmp"));
        byte[] payload = new byte[1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        String objectKey = "software/packages/abc/app.rpm";
        storage.completeUpload(putWholeObject(storage, objectKey, payload), objectKey, List.of(1));

        try (InputStream in = storage.readRange(objectKey, 100, 50)) {
            assertThat(in.readAllBytes()).isEqualTo(slice(payload, 100, 150));
        }
        // bounded read cannot overrun the requested length even near the end
        try (InputStream in = storage.readRange(objectKey, 1000, 500)) {
            assertThat(in.readAllBytes()).isEqualTo(slice(payload, 1000, 1024));
        }
        assertThatThrownBy(() -> storage.readRange(objectKey, 2000, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(storage.objectSize(objectKey)).isEqualTo(payload.length);
    }

    @Test
    void abortUpload_removesAllSessionTempBytes() throws Exception {
        LocalSoftwareTransferStorage storage = newStorage(tempDir.resolve("base"), tempDir.resolve("tmp"));
        String sessionRef = storage.initiateUpload("software/packages/abc/app.zip");
        byte[] bytes = "partial".getBytes(StandardCharsets.UTF_8);
        storage.stageChunk(sessionRef, 1, new ByteArrayInputStream(bytes), bytes.length);
        storage.commitChunk(sessionRef, "software/packages/abc/app.zip", 1);

        storage.abortUpload(sessionRef, "software/packages/abc/app.zip");

        assertThat(Files.exists(tempDir.resolve("tmp").resolve(sessionRef))).isFalse();
    }

    @Test
    void cleanupOrphanSessions_removesOnlyOldDirectories() throws Exception {
        LocalSoftwareTransferStorage storage = newStorage(tempDir.resolve("base"), tempDir.resolve("tmp"));
        String oldSession = storage.initiateUpload("software/packages/abc/old.zip");
        String freshSession = storage.initiateUpload("software/packages/abc/new.zip");
        // backdate the old session directory
        java.nio.file.Files.setLastModifiedTime(tempDir.resolve("tmp").resolve(oldSession),
                java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(3600)));

        int removed = storage.cleanupOrphanSessions(Instant.now().minusSeconds(60));

        assertThat(removed).isEqualTo(1);
        assertThat(Files.exists(tempDir.resolve("tmp").resolve(oldSession))).isFalse();
        assertThat(Files.exists(tempDir.resolve("tmp").resolve(freshSession))).isTrue();
    }

    @Test
    void traversalInObjectKey_isRejected() {
        LocalSoftwareTransferStorage storage = newStorage(tempDir.resolve("base"), tempDir.resolve("tmp"));
        assertThatThrownBy(() -> storage.completeUpload("s", "../escape", List.of(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String initiateAndStage(LocalSoftwareTransferStorage storage, String objectKey, byte[] payload)
            throws IOException {
        String sessionRef = storage.initiateUpload(objectKey);
        storage.stageChunk(sessionRef, 1, new ByteArrayInputStream(payload), payload.length);
        storage.commitChunk(sessionRef, objectKey, 1);
        return sessionRef;
    }

    private String putWholeObject(LocalSoftwareTransferStorage storage, String objectKey, byte[] payload)
            throws IOException {
        return initiateAndStage(storage, objectKey, payload);
    }

    private static byte[] slice(byte[] payload, int from, int to) {
        return java.util.Arrays.copyOfRange(payload, from, to);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
