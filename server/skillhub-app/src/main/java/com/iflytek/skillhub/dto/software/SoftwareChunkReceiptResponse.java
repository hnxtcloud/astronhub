package com.iflytek.skillhub.dto.software;

/** Result of accepting one chunk; retransmits of identical bytes are idempotent. */
public record SoftwareChunkReceiptResponse(
        int chunkNo,
        long byteSize,
        String sha256,
        boolean alreadyPresent
) {
}
