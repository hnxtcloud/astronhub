package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwarePolicyRevision;
import com.iflytek.skillhub.domain.software.SoftwarePolicyRevisionRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwarePolicyRevisionJpaRepository
        extends JpaRepository<SoftwarePolicyRevision, Long>, SoftwarePolicyRevisionRepository {

    @Override
    Optional<SoftwarePolicyRevision> findByRevision(long revision);

    @Override
    Page<SoftwarePolicyRevision> findAllByOrderByRevisionDesc(Pageable pageable);
}
