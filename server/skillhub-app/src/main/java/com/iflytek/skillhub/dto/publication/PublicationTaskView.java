package com.iflytek.skillhub.dto.publication;

import java.time.Instant;

public record PublicationTaskView(String id, String status, long revision, PublicationDraft draft,
        PublicationProgressView progress, String fileName, long byteSize, boolean materialReady,
        Instant createdAt, Instant updatedAt) {}
