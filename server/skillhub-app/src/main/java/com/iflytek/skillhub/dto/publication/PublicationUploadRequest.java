package com.iflytek.skillhub.dto.publication;

import jakarta.validation.constraints.*;

public record PublicationUploadRequest(@NotBlank @Size(max = 255) String fileName,
        @Positive long byteSize, boolean replace) {}
