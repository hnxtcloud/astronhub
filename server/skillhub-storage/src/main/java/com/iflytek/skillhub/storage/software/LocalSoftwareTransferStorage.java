package com.iflytek.skillhub.storage.software;

import com.iflytek.skillhub.storage.StorageAccessException;
import com.iflytek.skillhub.storage.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Local-disk transfer storage. Chunks land in a controlled temp directory and
 * the merge streams through a bounded buffer while hashing, so a 5 GiB package
 * never exists fully in memory.
 */
@Service
@ConditionalOnProperty(name = "skillhub.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalSoftwareTransferStorage implements SoftwareTransferStorage {

    private static final int MERGE_BUFFER_BYTES = 8 * 1024 * 1024;

    private final Path basePath;
    private final Path tempRoot;

    public LocalSoftwareTransferStorage(StorageProperties storageProperties,
                                        SoftwareTransferStorageProperties transferProperties) {
        this.basePath = Path.of(storageProperties.getLocal().getBasePath()).toAbsolutePath().normalize();
        this.tempRoot = Path.of(transferProperties.getTempDir()).toAbsolutePath().normalize();
    }

    @Override
    public String initiateUpload(String objectKey) {
        try {
            Files.createDirectories(tempRoot);
            String sessionId = "local-" + java.util.UUID.randomUUID();
            Files.createDirectories(sessionDir(sessionId));
            return sessionId;
        } catch (IOException e) {
            throw new StorageAccessException("initiateUpload", objectKey, e);
        }
    }

    @Override
    public StagedChunk stageChunk(String storageSessionId, int chunkNo, InputStream data, long declaredLength) {
        Path incoming = incomingPath(storageSessionId, chunkNo);
        try {
            Files.createDirectories(incoming.getParent());
            MessageDigest digest = sha256();
            long bytes = 0;
            try (OutputStream out = new java.security.DigestOutputStream(
                    Files.newOutputStream(incoming,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                    digest)) {
                byte[] buffer = new byte[MERGE_BUFFER_BYTES];
                int read;
                while ((read = data.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    bytes += read;
                }
            }
            if (bytes != declaredLength) {
                throw new StorageAccessException("stageChunk", incoming.toString(),
                        new IOException("received " + bytes + " bytes, expected " + declaredLength));
            }
            return new StagedChunk(bytes, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            deleteQuietly(incoming);
            throw new StorageAccessException("stageChunk", incoming.toString(), e);
        } catch (RuntimeException e) {
            deleteQuietly(incoming);
            throw e;
        }
    }

    @Override
    public void commitChunk(String storageSessionId, String objectKey, int chunkNo) {
        Path incoming = incomingPath(storageSessionId, chunkNo);
        Path committed = partPath(storageSessionId, chunkNo);
        try {
            if (!Files.exists(incoming)) {
                return;
            }
            Files.move(incoming, committed, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new StorageAccessException("commitChunk", committed.toString(), e);
        }
    }

    @Override
    public void discardChunk(String storageSessionId, int chunkNo) {
        deleteQuietly(incomingPath(storageSessionId, chunkNo));
    }

    @Override
    public CompleteResult completeUpload(String storageSessionId, String objectKey, List<Integer> orderedChunkNos) {
        Path target = resolveFinalPath(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".merging");
            MessageDigest digest = sha256();
            long total = 0;
            try (OutputStream out = new java.security.DigestOutputStream(
                    Files.newOutputStream(tmp,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                    digest)) {
                byte[] buffer = new byte[MERGE_BUFFER_BYTES];
                for (int chunkNo : orderedChunkNos) {
                    try (InputStream in = Files.newInputStream(partPath(storageSessionId, chunkNo))) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            total += read;
                        }
                    }
                }
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            deleteSessionDir(storageSessionId);
            return new CompleteResult(total, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            throw new StorageAccessException("completeUpload", objectKey, e);
        }
    }

    @Override
    public void abortUpload(String storageSessionId, String objectKey) {
        deleteSessionDir(storageSessionId);
    }

    @Override
    public long objectSize(String objectKey) {
        try {
            return Files.size(resolveFinalPath(objectKey));
        } catch (IOException e) {
            throw new StorageAccessException("objectSize", objectKey, e);
        }
    }

    @Override
    public InputStream readRange(String objectKey, long offset, long length) {
        Path path = resolveFinalPath(objectKey);
        try {
            long size = Files.size(path);
            if (offset < 0 || offset >= size) {
                throw new IllegalArgumentException("offset out of bounds: " + offset + " of " + size);
            }
            long bounded = Math.min(length, size - offset);
            InputStream raw = Files.newInputStream(path);
            raw.skipNBytes(offset);
            return new BoundedInputStream(raw, bounded);
        } catch (IOException e) {
            throw new StorageAccessException("readRange", objectKey, e);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        try {
            Files.deleteIfExists(resolveFinalPath(objectKey));
        } catch (IOException e) {
            throw new StorageAccessException("deleteObject", objectKey, e);
        }
    }

    /** Removes orphaned session temp dirs; returns the number of directories removed. */
    public int cleanupOrphanSessions(java.time.Instant olderThan) {
        if (!Files.isDirectory(tempRoot)) {
            return 0;
        }
        int removed = 0;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(tempRoot)) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try {
                    java.time.Instant modified = Files.getLastModifiedTime(dir).toInstant();
                    if (modified.isBefore(olderThan)) {
                        deleteRecursively(dir);
                        removed++;
                    }
                } catch (IOException e) {
                    // leave the directory for the next scan
                }
            }
        } catch (IOException e) {
            // temp root unreadable this round; retry on the next scan
        }
        return removed;
    }

    private Path sessionDir(String storageSessionId) {
        if (storageSessionId.contains("/") || storageSessionId.contains("\\") || storageSessionId.contains("..")) {
            throw new IllegalArgumentException("Invalid storage session id: " + storageSessionId);
        }
        return tempRoot.resolve(storageSessionId);
    }

    private Path partPath(String storageSessionId, int chunkNo) {
        return sessionDir(storageSessionId).resolve("part-" + chunkNo);
    }

    private Path incomingPath(String storageSessionId, int chunkNo) {
        return sessionDir(storageSessionId).resolve("part-" + chunkNo + ".incoming");
    }

    private Path resolveFinalPath(String objectKey) {
        if (objectKey.contains("\\")) {
            throw new IllegalArgumentException("Invalid storage key: " + objectKey);
        }
        Path resolved = basePath.resolve(objectKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid storage key: " + objectKey);
        }
        return resolved;
    }

    private void deleteSessionDir(String storageSessionId) {
        try {
            deleteRecursively(sessionDir(storageSessionId));
        } catch (IOException e) {
            throw new StorageAccessException("abortUpload", storageSessionId, e);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // next cleanup scan or discard retries
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Caps reads at exactly {@code remaining} bytes so callers cannot overrun. */
    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        private BoundedInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = delegate.read(buffer, off, (int) Math.min(len, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
