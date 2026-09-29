package com.iflytek.skillhub.domain.software;

import java.util.Optional;

public interface SoftwareStorageAccountRepository {

    SoftwareStorageAccount save(SoftwareStorageAccount account);

    Optional<SoftwareStorageAccount> findSingleton();
}
