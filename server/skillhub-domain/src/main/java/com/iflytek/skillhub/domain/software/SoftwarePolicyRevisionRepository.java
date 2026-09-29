package com.iflytek.skillhub.domain.software;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Repository port for immutable software policy revisions.
 */
public interface SoftwarePolicyRevisionRepository {

    SoftwarePolicyRevision save(SoftwarePolicyRevision revision);

    Optional<SoftwarePolicyRevision> findById(Long id);

    Optional<SoftwarePolicyRevision> findByRevision(long revision);

    Page<SoftwarePolicyRevision> findAllByOrderByRevisionDesc(Pageable pageable);
}
