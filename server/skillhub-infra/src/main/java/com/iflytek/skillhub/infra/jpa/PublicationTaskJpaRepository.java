package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.publication.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Uses explicit FOR UPDATE SQL for the same row lock on PostgreSQL and H2 integration tests. */
public interface PublicationTaskJpaRepository extends JpaRepository<PublicationTask, String>, PublicationTaskRepository {
    @Override
    @Query(value = "select * from publication_task where id = :id for update", nativeQuery = true)
    Optional<PublicationTask> lockById(@Param("id") String id);
}
