package com.iflytek.skillhub.domain.software;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SoftwareQuotaServiceTest {

    private SoftwareStorageAccountRepository accountRepository;
    private SoftwareStorageAccount account;
    private SoftwareQuotaService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(SoftwareStorageAccountRepository.class);
        account = new SoftwareStorageAccount(100, 50);
        when(accountRepository.findSingleton()).thenReturn(Optional.of(account));
        when(accountRepository.save(any(SoftwareStorageAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new SoftwareQuotaService(accountRepository);
    }

    @Test
    void reserve_addsToReservedWithinQuota() {
        service.reserve(200, 400);
        assertThat(account.getReservedBytes()).isEqualTo(250);
    }

    @Test
    void reserve_rejectsWhenUsedPlusReservedWouldExceedQuota() {
        assertThatThrownBy(() -> service.reserve(400, 400))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("error.software.upload.quotaExceeded");
        assertThat(account.getReservedBytes()).isEqualTo(50);
    }

    @Test
    void confirmReservation_movesReservationIntoUsed() {
        service.confirmReservation(50, 50);
        assertThat(account.getReservedBytes()).isZero();
        assertThat(account.getUsedBytes()).isEqualTo(150);
    }

    @Test
    void releaseReservation_subtractsWithoutTouchingUsed() {
        service.releaseReservation(50);
        assertThat(account.getReservedBytes()).isZero();
        assertThat(account.getUsedBytes()).isEqualTo(100);
    }

    @Test
    void releaseUsed_subtractsConfirmedUsageAfterVerifiedDeletion() {
        service.releaseUsed(100);
        assertThat(account.getUsedBytes()).isZero();
    }

    @Test
    void missingAccount_failsFast() {
        when(accountRepository.findSingleton()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reserve(1, 10))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.software.quota.accountMissing");
        verify(accountRepository).findSingleton();
    }

    @Test
    void totalCommittedBytes_sumsUsedAndReserved() {
        assertThat(service.totalCommittedBytes()).isEqualTo(150);
    }
}
