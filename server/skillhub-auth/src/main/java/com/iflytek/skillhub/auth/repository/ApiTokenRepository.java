package com.iflytek.skillhub.auth.repository;

import com.iflytek.skillhub.auth.entity.ApiToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for personal API tokens and token listings scoped to one user.
 */
@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {
    /** Usage telemetry must never merge a stale credential over revocation or scope changes. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE ApiToken t SET t.lastUsedAt = :now WHERE t.id = :id AND t.revokedAt IS NULL")
    int updateLastUsedAt(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    Optional<ApiToken> findByTokenHash(String tokenHash);
    List<ApiToken> findByUserId(String userId);
    List<ApiToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(String userId);
    Page<ApiToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);
    boolean existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase(String userId, String name);
    Optional<ApiToken> findByUserIdAndNameIgnoreCaseAndRevokedAtIsNull(String userId, String name);
}
