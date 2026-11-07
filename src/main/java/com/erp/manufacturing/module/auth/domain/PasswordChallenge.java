package com.erp.manufacturing.module.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One emailed 6-digit code. {@code userId} is null for a decoy row that only exists so an unknown
 * RESET identifier is indistinguishable from a real one. The code is never stored — see
 * {@code codeHash} in {@code PasswordFlowService}.
 *
 * <p>State moves only through the guarded UPDATE queries in {@code PasswordChallengeRepository}
 * ({@code attempts}, {@code status}); the setters exist for the builder-created insert.
 */
@Entity
@Table(name = "password_challenges")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChallenge {

    @Id
    @Column(name = "challenge_id", updatable = false, nullable = false)
    private UUID challengeId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, length = 10)
    private PasswordPurpose purpose;

    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    @Builder.Default
    private PasswordChallengeStatus status = PasswordChallengeStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "resend_at", nullable = false, updatable = false)
    private Instant resendAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
