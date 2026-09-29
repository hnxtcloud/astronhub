package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.software.SoftwareStorageAccount;
import com.iflytek.skillhub.domain.software.SoftwareStorageAccountRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwareStorageAccountJpaRepository
        extends JpaRepository<SoftwareStorageAccount, Long>, SoftwareStorageAccountRepository {

    /**
     * Pessimistic row lock: concurrent session admissions serialize here so
     * the quota check can never overcommit. Native FOR UPDATE because the
     * portable pessimistic-lock hint emits "FOR NO KEY UPDATE", which H2 in
     * PostgreSQL mode rejects.
     */
    @Override
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT id, used_bytes, reserved_bytes, entity_version "
                    + "FROM software_storage_account WHERE id = "
                    + SoftwareStorageAccount.SINGLETON_ID + " FOR UPDATE",
            nativeQuery = true)
    Optional<SoftwareStorageAccount> findSingleton();
}
