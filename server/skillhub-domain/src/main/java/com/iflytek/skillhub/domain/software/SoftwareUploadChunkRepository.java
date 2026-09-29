package com.iflytek.skillhub.domain.software;

import java.util.List;
import java.util.Optional;

public interface SoftwareUploadChunkRepository {

    SoftwareUploadChunk save(SoftwareUploadChunk chunk);

    Optional<SoftwareUploadChunk> findBySessionIdAndChunkNo(Long sessionId, int chunkNo);

    List<SoftwareUploadChunk> findBySessionId(Long sessionId);

    long countBySessionId(Long sessionId);

    void deleteAllIn(List<SoftwareUploadChunk> chunks);
}
