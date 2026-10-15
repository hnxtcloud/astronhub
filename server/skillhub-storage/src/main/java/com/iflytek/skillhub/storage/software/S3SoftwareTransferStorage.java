package com.iflytek.skillhub.storage.software;

import com.iflytek.skillhub.storage.S3StorageProperties;
import com.iflytek.skillhub.storage.StorageAccessException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

/**
 * S3-backed transfer storage using native multipart uploads. Chunk digests are
 * computed while staging to the local temp directory; the completed object's
 * SHA-256 comes from one full streaming read of the merged object, never from
 * the S3 ETag. Provider part etags are recovered via ListParts so a lost
 * completion can be retried safely.
 */
@Service
@ConditionalOnProperty(name = "skillhub.storage.provider", havingValue = "s3")
public class S3SoftwareTransferStorage implements SoftwareTransferStorage {

    private static final Logger log = LoggerFactory.getLogger(S3SoftwareTransferStorage.class);
    private static final int MERGE_BUFFER_BYTES = 8 * 1024 * 1024;

    private final S3StorageProperties properties;
    private final SoftwareTransferStorageProperties transferProperties;
    private S3Client s3Client;

    public S3SoftwareTransferStorage(S3StorageProperties properties,
                                     SoftwareTransferStorageProperties transferProperties) {
        this.properties = properties;
        this.transferProperties = transferProperties;
    }

    @PostConstruct
    void init() {
        this.s3Client = buildClient();
    }

    /** Overridable for tests. */
    protected S3Client buildClient() {
        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder()
                .maxConnections(properties.getMaxConnections())
                .connectionAcquisitionTimeout(properties.getConnectionAcquisitionTimeout());
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(buildCredentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isForcePathStyle())
                        .chunkedEncodingEnabled(!properties.isDisableChunkedEncoding())
                        .build())
                .httpClientBuilder(httpClientBuilder)
                .overrideConfiguration(config -> config
                        .apiCallAttemptTimeout(properties.getApiCallAttemptTimeout())
                        .apiCallTimeout(properties.getApiCallTimeout()));
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    private AwsCredentialsProvider buildCredentialsProvider() {
        if (properties.getAccessKey() != null && !properties.getAccessKey().isBlank()
                && properties.getSecretKey() != null && !properties.getSecretKey().isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    @Override
    public String initiateUpload(String objectKey) {
        try {
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(
                    CreateMultipartUploadRequest.builder()
                            .bucket(properties.getBucket())
                            .key(objectKey)
                            .build());
            return response.uploadId();
        } catch (RuntimeException e) {
            throw new StorageAccessException("initiateUpload", objectKey, e);
        }
    }

    @Override
    public StagedChunk stageChunk(String storageSessionId, int chunkNo, InputStream data, long declaredLength) {
        Path staged = stagedPath(storageSessionId, chunkNo);
        try {
            Files.createDirectories(staged.getParent());
            MessageDigest digest = sha256();
            long bytes = 0;
            try (OutputStream out = new java.security.DigestOutputStream(
                    Files.newOutputStream(staged,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING),
                    digest)) {
                byte[] buffer = new byte[MERGE_BUFFER_BYTES];
                int read;
                while ((read = data.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    bytes += read;
                }
            }
            if (bytes != declaredLength) {
                throw new StorageAccessException("stageChunk", staged.toString(),
                        new IOException("received " + bytes + " bytes, expected " + declaredLength));
            }
            return new StagedChunk(bytes, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            deleteQuietly(staged);
            throw new StorageAccessException("stageChunk", staged.toString(), e);
        } catch (RuntimeException e) {
            deleteQuietly(staged);
            throw e;
        }
    }

    @Override
    public void commitChunk(String storageSessionId, String objectKey, int chunkNo) {
        Path staged = stagedPath(storageSessionId, chunkNo);
        if (!Files.exists(staged)) {
            return;
        }
        try {
            UploadPartResponse response = s3Client.uploadPart(UploadPartRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .uploadId(storageSessionId)
                    .partNumber(chunkNo)
                    .contentLength(Files.size(staged))
                    .build(), RequestBody.fromFile(staged));
            log.debug("Uploaded part {} for session {} (etag {})", chunkNo, storageSessionId, response.eTag());
            Files.deleteIfExists(staged);
        } catch (IOException | RuntimeException e) {
            throw new StorageAccessException("commitChunk", storageSessionId + "#" + chunkNo, e);
        }
    }

    @Override
    public void discardChunk(String storageSessionId, int chunkNo) {
        deleteQuietly(stagedPath(storageSessionId, chunkNo));
    }

    @Override
    public CompleteResult completeUpload(String storageSessionId, String objectKey, List<Integer> orderedChunkNos) {
        try {
            List<CompletedPart> parts = recoverParts(storageSessionId, objectKey, orderedChunkNos);
            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .uploadId(storageSessionId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                    .build());
            long size = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket()).key(objectKey).build()).contentLength();
            String sha256 = hashWholeObject(objectKey, size);
            return new CompleteResult(size, sha256);
        } catch (RuntimeException e) {
            throw new StorageAccessException("completeUpload", objectKey, e);
        }
    }

    @Override
    public void abortUpload(String storageSessionId, String objectKey) {
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .uploadId(storageSessionId)
                    .build());
        } catch (RuntimeException e) {
            // The multipart abort is best-effort here; S3 lifecycle rules and the
            // cleanup task still reclaim orphaned parts.
            log.warn("Failed to abort multipart upload {} (non-fatal): {}",
                    storageSessionId, e.getMessage());
        } finally {
            deleteSessionStaging(storageSessionId);
        }
    }

    @Override
    public void abortUploadVerified(String storageSessionId, String objectKey) {
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(properties.getBucket()).key(objectKey).uploadId(storageSessionId).build());
        } catch (software.amazon.awssdk.services.s3.model.S3Exception ex) {
            // A completed/already-aborted multipart session is also a confirmed absence.
            if (ex.statusCode() != 404) throw new StorageAccessException("abortUploadVerified", objectKey, ex);
        } catch (RuntimeException ex) {
            throw new StorageAccessException("abortUploadVerified", objectKey, ex);
        }
        deleteSessionStaging(storageSessionId);
    }

    @Override
    public long objectSize(String objectKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket()).key(objectKey).build()).contentLength();
        } catch (RuntimeException e) {
            throw new StorageAccessException("objectSize", objectKey, e);
        }
    }

    @Override
    public InputStream readRange(String objectKey, long offset, long length) {
        if (offset < 0 || length <= 0) {
            throw new IllegalArgumentException("invalid range: offset=" + offset + " length=" + length);
        }
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .range("bytes=" + offset + "-" + (offset + length - 1))
                    .build());
        } catch (RuntimeException e) {
            throw new StorageAccessException("readRange", objectKey, e);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        try {
            s3Client.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
        } catch (RuntimeException e) {
            throw new StorageAccessException("deleteObject", objectKey, e);
        }
    }

    private List<CompletedPart> recoverParts(String storageSessionId, String objectKey,
                                             List<Integer> orderedChunkNos) {
        List<PartSummary> parts = new ArrayList<>();
        Integer partNumberMarker = null;
        do {
            ListPartsRequest.Builder request = ListPartsRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .uploadId(storageSessionId);
            if (partNumberMarker != null) {
                request.partNumberMarker(partNumberMarker);
            }
            ListPartsResponse response = s3Client.listParts(request.build());
            response.parts().forEach(part -> parts.add(
                    new PartSummary(part.partNumber(), part.eTag(), part.size())));
            partNumberMarker = response.nextPartNumberMarker();
            if (response.isTruncated() == null || !response.isTruncated()) {
                break;
            }
        } while (true);

        List<CompletedPart> completed = new ArrayList<>();
        for (int chunkNo : orderedChunkNos) {
            PartSummary match = parts.stream()
                    .filter(part -> part.number() == chunkNo)
                    .findFirst()
                    .orElseThrow(() -> new StorageAccessException("completeUpload", objectKey,
                            new IOException("part " + chunkNo + " missing on provider")));
            completed.add(CompletedPart.builder()
                    .partNumber(match.number())
                    .eTag(match.etag())
                    .build());
        }
        return completed;
    }

    private String hashWholeObject(String objectKey, long size) throws StorageAccessException {
        MessageDigest digest = sha256();
        try (InputStream in = s3Client.getObject(GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build())) {
            byte[] buffer = new byte[MERGE_BUFFER_BYTES];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                total += read;
            }
            if (total != size) {
                throw new IOException("hashed " + total + " bytes but object reports " + size);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new StorageAccessException("hashWholeObject", objectKey, e);
        }
    }

    private Path sessionStagingDir(String storageSessionId) {
        if (storageSessionId.contains("/") || storageSessionId.contains("\\") || storageSessionId.contains("..")) {
            throw new IllegalArgumentException("Invalid storage session id: " + storageSessionId);
        }
        return Path.of(transferProperties.getTempDir()).toAbsolutePath().normalize()
                .resolve("s3-" + storageSessionId);
    }

    private Path stagedPath(String storageSessionId, int chunkNo) {
        return sessionStagingDir(storageSessionId).resolve("part-" + chunkNo);
    }

    private void deleteSessionStaging(String storageSessionId) {
        Path dir = sessionStagingDir(storageSessionId);
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
        } catch (IOException | UncheckedIOException e) {
            log.warn("Failed to clean S3 chunk staging dir {} (non-fatal)", dir, e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // next cleanup scan retries
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record PartSummary(int number, String etag, long size) {
    }
}
