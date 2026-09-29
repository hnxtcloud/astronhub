package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwarePolicyState;
import com.iflytek.skillhub.domain.software.SoftwarePolicyStateRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwarePolicyStateJpaRepository
        extends JpaRepository<SoftwarePolicyState, Long>, SoftwarePolicyStateRepository {

    @Override
    default Optional<SoftwarePolicyState> findSingleton() {
        return findById(SoftwarePolicyState.SINGLETON_ID);
    }

    @Override
    default SoftwarePolicyState requireSingleton() {
        return findSingleton()
                .orElseThrow(() -> new DomainBadRequestException("error.software.settings.notInitialized"));
    }
}
