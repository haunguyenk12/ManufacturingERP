package com.erp.manufacturing.module.auth.repository;

import com.erp.manufacturing.module.auth.domain.PasswordChallenge;
import com.erp.manufacturing.module.auth.domain.PasswordChallengeStatus;
import com.erp.manufacturing.module.auth.domain.PasswordPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Every state change is a single guarded UPDATE so two concurrent requests cannot both win: the
 * affected-row count, not a prior read, decides who consumed the code or the token.
 */
public interface PasswordChallengeRepository extends JpaRepository<PasswordChallenge, UUID> {

    Optional<PasswordChallenge> findFirstByUserIdAndPurposeAndStatusOrderByCreatedAtDesc(
            UUID userId, PasswordPurpose purpose, PasswordChallengeStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordChallenge c set c.attempts = c.attempts + 1 where c.challengeId = :id")
    int incrementAttempts(@Param("id") UUID id);

    /** PENDING → VERIFIED, only while the code is live and attempts are not exhausted. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordChallenge c set c.status = 'VERIFIED', c.verifiedAt = :now
            where c.challengeId = :id and c.status = 'PENDING' and c.expiresAt > :now and c.attempts < :maxAttempts
            """)
    int markVerified(@Param("id") UUID id, @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);

    /** VERIFIED → COMPLETED: the single-use spend of the setup token. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordChallenge c set c.status = 'COMPLETED', c.completedAt = :now
            where c.challengeId = :id and c.status = 'VERIFIED'
            """)
    int markCompleted(@Param("id") UUID id, @Param("now") Instant now);

    /** Retires a challenge that is about to be replaced; no-op once COMPLETED. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordChallenge c set c.status = 'SUPERSEDED'
            where c.challengeId = :id and c.status in ('PENDING', 'VERIFIED')
            """)
    int supersede(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PasswordChallenge c where c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
