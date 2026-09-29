package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwareUploadChunk;
import com.iflytek.skillhub.domain.software.SoftwareUploadChunkRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwareUploadChunkJpaRepository
        extends JpaRepository<SoftwareUploadChunk, Long>, SoftwareUploadChunkRepository {

    @Override
    Optional<SoftwareUploadChunk> findBySessionIdAndChunkNo(Long sessionId, int chunkNo);

    @Override
    List<SoftwareUploadChunk> findBySessionId(Long sessionId);

    @Override
    long countBySessionId(Long sessionId);

    @Override
    default void deleteAllIn(List<SoftwareUploadChunk> chunks) {
        deleteAll(chunks);
    }
}
