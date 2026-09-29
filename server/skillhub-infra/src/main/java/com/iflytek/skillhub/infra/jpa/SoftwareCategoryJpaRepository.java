package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwareCategory;
import com.iflytek.skillhub.domain.software.SoftwareCategoryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwareCategoryJpaRepository
        extends JpaRepository<SoftwareCategory, Long>, SoftwareCategoryRepository {

    @Override
    Optional<SoftwareCategory> findByCode(String code);

    @Override
    List<SoftwareCategory> findAllByOrderBySortOrderAscIdAsc();

    @Override
    long countByCode(String code);
}
