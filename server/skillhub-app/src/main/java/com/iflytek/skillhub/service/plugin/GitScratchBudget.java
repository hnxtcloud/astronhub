package com.iflytek.skillhub.service.plugin;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/** Bounded inspection of the private scratch tree while a Git process is writing it. */
final class GitScratchBudget {
    /** Filesystem traversal boundary; attributes describe the entry at its observation time. */
    @FunctionalInterface
    interface DirectoryWalk {
        void walk(Path root, FileVisitor<Path> visitor) throws IOException;
    }

    private final long maximumBytes;
    private final DirectoryWalk filesystem;

    GitScratchBudget(long maximumBytes) {
        this(maximumBytes, (root, visitor) -> Files.walkFileTree(root, visitor));
    }

    GitScratchBudget(long maximumBytes, DirectoryWalk filesystem) {
        if (maximumBytes < 0) throw new IllegalArgumentException("Negative scratch budget");
        this.maximumBytes = maximumBytes;
        this.filesystem = filesystem;
    }

    void check(Path root) throws IOException {
        Path boundary = root.toAbsolutePath().normalize();
        filesystem.walk(
                boundary,
                new SimpleFileVisitor<>() {
                    private long bytes;

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                            throws IOException {
                        if (attributes.isRegularFile()) {
                            // Git may have renamed/unlinked this path since traversal observed it.
                            long size = attributes.size();
                            if (size > maximumBytes - bytes)
                                throw new GitMarketplacePublisher.Failure("GIT_SCRATCH_LIMIT");
                            bytes += size;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException failure)
                            throws IOException {
                        return disappearedEntry(file, failure);
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                            throws IOException {
                        return failure == null
                                ? FileVisitResult.CONTINUE
                                : disappearedEntry(directory, failure);
                    }

                    private FileVisitResult disappearedEntry(Path entry, IOException failure)
                            throws IOException {
                        Path absolute = entry.toAbsolutePath().normalize();
                        if (failure instanceof NoSuchFileException
                                && absolute.startsWith(boundary)
                                && !absolute.equals(boundary)) return FileVisitResult.CONTINUE;
                        throw failure;
                    }
                });
    }
}
