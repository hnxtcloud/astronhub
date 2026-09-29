package com.iflytek.skillhub.domain.software;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Singleton capacity account for the software center. {@code usedBytes} counts
 * confirmed package bytes; {@code reservedBytes} counts pending upload
 * sessions. Session creation serializes on this row so concurrent admissions
 * can never overcommit the logical quota. Bytes are released only after the
 * corresponding storage deletion is confirmed; a failed compensation keeps
 * the accounting.
 */
@Entity
@Table(name = "software_storage_account")
public class SoftwareStorageAccount {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "used_bytes", nullable = false)
    private long usedBytes;

    @Column(name = "reserved_bytes", nullable = false)
    private long reservedBytes;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long entityVersion;

    protected SoftwareStorageAccount() {
    }

    public SoftwareStorageAccount(long usedBytes, long reservedBytes) {
        this.id = SINGLETON_ID;
        this.usedBytes = usedBytes;
        this.reservedBytes = reservedBytes;
    }

    public Long getId() {
        return id;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getReservedBytes() {
        return reservedBytes;
    }

    /** Adds an in-flight reservation; the caller enforced the quota check. */
    public void reserve(long bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("reservation must be positive");
        }
        this.reservedBytes += bytes;
    }

    /** A completed upload moves its reservation into confirmed usage. */
    public void confirmReservation(long reservedBytes, long actualBytes) {
        this.reservedBytes -= reservedBytes;
        this.usedBytes += actualBytes;
    }

    /** Releases a reservation when a session is cancelled, expired or failed. */
    public void releaseReservation(long bytes) {
        this.reservedBytes -= bytes;
    }

    /** Moves confirmed usage out when the bytes are verifiably gone. */
    public void releaseUsed(long bytes) {
        this.usedBytes -= bytes;
    }
}
