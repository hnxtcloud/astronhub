package com.iflytek.skillhub.domain.publication;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PublicationTaskRepository {
    PublicationTask save(PublicationTask task);
    Optional<PublicationTask> findById(String id);
    Optional<PublicationTask> lockById(String id);
    Page<PublicationTask> findByOwnerIdOrderByUpdatedAtDesc(String ownerId, Pageable page);
}
