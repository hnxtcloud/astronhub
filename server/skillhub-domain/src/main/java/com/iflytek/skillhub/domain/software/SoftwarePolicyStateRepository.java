package com.iflytek.skillhub.domain.software;

import java.util.Optional;

/**
 * Repository port for the singleton software policy state pointer.
 */
public interface SoftwarePolicyStateRepository {

    SoftwarePolicyState save(SoftwarePolicyState state);

    /** Returns the singleton pointer row, or empty when policy is unseeded. */
    Optional<SoftwarePolicyState> findSingleton();

    SoftwarePolicyState requireSingleton();
}
