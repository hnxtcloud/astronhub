package com.iflytek.skillhub.domain.software;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Capacity accounting against the logical quota. The account row is locked
 * pessimistically so two concurrent session admissions serialize instead of
 * both passing the same quota check.
 */
@Service
public class SoftwareQuotaService {

    private final SoftwareStorageAccountRepository accountRepository;

    public SoftwareQuotaService(SoftwareStorageAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** Reserves capacity for a new upload session inside the caller's transaction. */
    @Transactional
    public void reserve(long expectedBytes, long totalQuotaBytes) {
        SoftwareStorageAccount account = requireAccount();
        if (account.getUsedBytes() + account.getReservedBytes() + expectedBytes > totalQuotaBytes) {
            throw new DomainConflictException("error.software.upload.quotaExceeded",
                    account.getUsedBytes() + account.getReservedBytes(), totalQuotaBytes);
        }
        account.reserve(expectedBytes);
        accountRepository.save(account);
    }

    /** Moves a finished upload from reservation into confirmed usage. */
    @Transactional
    public void confirmReservation(long reservedBytes, long actualBytes) {
        SoftwareStorageAccount account = requireAccount();
        account.confirmReservation(reservedBytes, actualBytes);
        accountRepository.save(account);
    }

    /** Releases a reservation for a cancelled, expired or failed session. */
    @Transactional
    public void releaseReservation(long reservedBytes) {
        SoftwareStorageAccount account = requireAccount();
        account.releaseReservation(reservedBytes);
        accountRepository.save(account);
    }

    /** Moves confirmed usage out once the bytes are verifiably deleted. */
    @Transactional
    public void releaseUsed(long bytes) {
        SoftwareStorageAccount account = requireAccount();
        account.releaseUsed(bytes);
        accountRepository.save(account);
    }

    public long totalCommittedBytes() {
        SoftwareStorageAccount account = requireAccount();
        return account.getUsedBytes() + account.getReservedBytes();
    }

    private SoftwareStorageAccount requireAccount() {
        return accountRepository.findSingleton()
                .orElseThrow(() -> new DomainBadRequestException("error.software.quota.accountMissing"));
    }
}
