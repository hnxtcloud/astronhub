package com.iflytek.skillhub.storage.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.storage.S3StorageProperties;
import com.iflytek.skillhub.storage.StorageAccessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

/**
 * Verifies the S3 multipart request construction and digest semantics with a
 * mocked provider; live bucket behavior is validated in staging.
 */
class S3SoftwareTransferStorageTest {

    private static final String BUCKET = "test-bucket";
    private static final String OBJECT_KEY = "software/packages/abc/app.msi";

    private S3Client s3Client;
    private TestableS3TransferStorage storage;

    private static class TestableS3TransferStorage extends S3SoftwareTransferStorage {
        private final S3Client mockClient;

        TestableS3TransferStorage(S3StorageProperties properties,
                                  SoftwareTransferStorageProperties transferProperties,
                                  S3Client mockClient) {
            super(properties, transferProperties);
            this.mockClient = mockClient;
        }

        @Override
        protected S3Client buildClient() {
            return mockClient;
        }
    }

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        S3StorageProperties properties = new S3StorageProperties();
        properties.setBucket(BUCKET);
        properties.setRegion("us-east-1");
        properties.setAccessKey("key");
        properties.setSecretKey("secret");
        SoftwareTransferStorageProperties transferProperties = new SoftwareTransferStorageProperties();
        transferProperties.setTempDir("./target/s3-transfer-test-tmp");
        storage = new TestableS3TransferStorage(properties, transferProperties, s3Client);
        storage.init();
    }

    @Test
    void initiateUpload_createsMultipartSession() {
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload-1").build());

        String ref = storage.initiateUpload(OBJECT_KEY);

        assertThat(ref).isEqualTo("upload-1");
        verify(s3Client).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    @Test
    void commitChunk_uploadsStagedBytesWithPartNumber() throws Exception {
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload-1").build());
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag("\"etag-1\"").build());
        String ref = storage.initiateUpload(OBJECT_KEY);

        byte[] bytes = "chunk-bytes".getBytes(StandardCharsets.UTF_8);
        SoftwareTransferStorage.StagedChunk staged =
                storage.stageChunk(ref, 1, new ByteArrayInputStream(bytes), bytes.length);

        assertThat(staged.byteSize()).isEqualTo(bytes.length);
        assertThat(staged.sha256Hex()).isEqualTo(HexFormat.of().formatHex(sha256(bytes)));

        storage.commitChunk(ref, OBJECT_KEY, 1);

        verify(s3Client).uploadPart(
                argThat((UploadPartRequest r) -> r.partNumber() == 1
                        && r.uploadId().equals("upload-1")
                        && r.key().equals(OBJECT_KEY)
                        && r.contentLength() == (long) bytes.length),
                any(RequestBody.class));
    }

    @Test
    void completeUpload_recoversPartsFromProviderAndHashesFullObject() throws Exception {
        when(s3Client.listParts(any(ListPartsRequest.class)))
                .thenReturn(ListPartsResponse.builder()
                        .parts(Part.builder().partNumber(1).eTag("\"e1\"").size(8L).build(),
                                Part.builder().partNumber(2).eTag("\"e2\"").size(4L).build())
                        .isTruncated(false)
                        .build());
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().build());
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(12L).build());
        byte[] full = "0123456789ab".getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseStream(full));

        SoftwareTransferStorage.CompleteResult result = storage.completeUpload(
                "upload-1", OBJECT_KEY, List.of(1, 2));

        assertThat(result.byteSize()).isEqualTo(12L);
        assertThat(result.sha256Hex()).isEqualTo(HexFormat.of().formatHex(sha256(full)));
        verify(s3Client).completeMultipartUpload(argThat((CompleteMultipartUploadRequest r) ->
                r.multipartUpload().parts().size() == 2
                        && r.multipartUpload().parts().get(0).partNumber() == 1
                        && r.multipartUpload().parts().get(0).eTag().equals("\"e1\"")));
        // digest comes from a full read, never from the etag
        verify(s3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void completeUpload_missingProviderPart_fails() {
        when(s3Client.listParts(any(ListPartsRequest.class)))
                .thenReturn(ListPartsResponse.builder()
                        .parts(Part.builder().partNumber(1).eTag("\"e1\"").size(8L).build())
                        .isTruncated(false)
                        .build());

        assertThatThrownBy(() -> storage.completeUpload("upload-1", OBJECT_KEY, List.of(1, 2)))
                .isInstanceOf(StorageAccessException.class);
    }

    @Test
    void abortUpload_abortsMultipartSession() {
        storage.abortUpload("upload-1", OBJECT_KEY);
        verify(s3Client).abortMultipartUpload(argThat((AbortMultipartUploadRequest r) ->
                r.uploadId().equals("upload-1") && r.key().equals(OBJECT_KEY)));
    }

    @Test
    void readRange_usesInclusiveByteRange() throws Exception {
        byte[] slice = "middle".getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseStream(slice));

        try (InputStream in = storage.readRange(OBJECT_KEY, 4, 6)) {
            assertThat(in.readAllBytes()).isEqualTo(slice);
        }
        verify(s3Client).getObject(argThat((GetObjectRequest r) ->
                r.range().equals("bytes=4-9")));
    }

    @Test
    void commitChunk_withoutStagedBytes_isSkippedWithoutProviderCall() {
        storage.commitChunk("upload-1", OBJECT_KEY, 1);
        verify(s3Client, never()).uploadPart(any(UploadPartRequest.class), any(RequestBody.class));
    }

    private static ResponseInputStream<GetObjectResponse> responseStream(byte[] bytes) {
        return new ResponseInputStream<>(GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes)));
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
