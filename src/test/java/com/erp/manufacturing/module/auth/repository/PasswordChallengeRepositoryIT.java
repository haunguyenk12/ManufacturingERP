package com.erp.manufacturing.module.auth.repository;

import com.erp.manufacturing.common.AbstractPostgresIntegrationTest;
import com.erp.manufacturing.module.auth.domain.PasswordChallenge;
import com.erp.manufacturing.module.auth.domain.PasswordChallengeStatus;
import com.erp.manufacturing.module.auth.domain.PasswordPurpose;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guarded UPDATEs are the whole concurrency story of the password flows: the affected-row count,
 * not a prior read, decides who consumed a code or a token. That logic lives in JPQL, so only a real
 * database can prove it (rule R7) — a mock repository would just echo whatever the test told it.
 *
 * <p>Rows use {@code user_id = NULL} (the decoy shape) to stay clear of the users foreign key; the
 * status / attempts / expiry conditions are identical for real rows.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PasswordChallengeRepository – guarded state changes")
class PasswordChallengeRepositoryIT extends AbstractPostgresIntegrationTest {

    private static final int MAX = 5;

    @Autowired
    PasswordChallengeRepository repository;

    @Test
    @DisplayName("markVerified: PENDING → VERIFIED once; the second caller affects 0 rows")
    void markVerified_onlyOneCallerWins() {
        UUID id = persist(PasswordChallengeStatus.PENDING, 0, Instant.now().plus(5, ChronoUnit.MINUTES));

        assertThat(repository.markVerified(id, Instant.now(), MAX)).isEqualTo(1);
        assertThat(repository.markVerified(id, Instant.now(), MAX)).isZero();

        PasswordChallenge row = repository.findById(id).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(PasswordChallengeStatus.VERIFIED);
        assertThat(row.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("markVerified: refuses an expired code and a code whose attempts are used up")
    void markVerified_refusesExpiredOrExhausted() {
        UUID expired = persist(PasswordChallengeStatus.PENDING, 0, Instant.now().minusSeconds(1));
        UUID exhausted = persist(PasswordChallengeStatus.PENDING, MAX, Instant.now().plus(5, ChronoUnit.MINUTES));
        UUID lastChance = persist(PasswordChallengeStatus.PENDING, MAX - 1, Instant.now().plus(5, ChronoUnit.MINUTES));

        assertThat(repository.markVerified(expired, Instant.now(), MAX)).isZero();
        assertThat(repository.markVerified(exhausted, Instant.now(), MAX)).isZero();
        assertThat(repository.markVerified(lastChance, Instant.now(), MAX)).isEqualTo(1);
        assertThat(repository.findById(expired).orElseThrow().getStatus()).isEqualTo(PasswordChallengeStatus.PENDING);
    }

    @Test
    @DisplayName("markCompleted: only a VERIFIED challenge can be spent, and only once")
    void markCompleted_isSingleUse() {
        UUID pending = persist(PasswordChallengeStatus.PENDING, 0, Instant.now().plus(5, ChronoUnit.MINUTES));
        UUID verified = persist(PasswordChallengeStatus.VERIFIED, 0, Instant.now().plus(5, ChronoUnit.MINUTES));

        assertThat(repository.markCompleted(pending, Instant.now())).isZero();
        assertThat(repository.markCompleted(verified, Instant.now())).isEqualTo(1);
        assertThat(repository.markCompleted(verified, Instant.now())).isZero();
        assertThat(repository.findById(verified).orElseThrow().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("supersede: retires PENDING and VERIFIED, never rewrites a COMPLETED challenge")
    void supersede_leavesCompletedAlone() {
        UUID pending = persist(PasswordChallengeStatus.PENDING, 0, Instant.now().plus(5, ChronoUnit.MINUTES));
        UUID completed = persist(PasswordChallengeStatus.COMPLETED, 0, Instant.now().plus(5, ChronoUnit.MINUTES));

        assertThat(repository.supersede(pending)).isEqualTo(1);
        assertThat(repository.supersede(completed)).isZero();
        assertThat(repository.findById(completed).orElseThrow().getStatus()).isEqualTo(PasswordChallengeStatus.COMPLETED);
    }

    @Test
    @DisplayName("incrementAttempts adds exactly one per call")
    void incrementAttempts_addsOne() {
        UUID id = persist(PasswordChallengeStatus.PENDING, 0, Instant.now().plus(5, ChronoUnit.MINUTES));

        repository.incrementAttempts(id);
        repository.incrementAttempts(id);

        assertThat(repository.findById(id).orElseThrow().getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("deleteExpiredBefore removes only challenges that expired before the cutoff")
    void deleteExpiredBefore_keepsRecentRows() {
        UUID old = persist(PasswordChallengeStatus.PENDING, 0, Instant.now().minus(2, ChronoUnit.DAYS));
        UUID live = persist(PasswordChallengeStatus.PENDING, 0, Instant.now().plus(5, ChronoUnit.MINUTES));

        assertThat(repository.deleteExpiredBefore(Instant.now().minus(1, ChronoUnit.DAYS))).isEqualTo(1);

        assertThat(repository.findById(old)).isEmpty();
        assertThat(repository.findById(live)).isPresent();
    }

    private UUID persist(PasswordChallengeStatus status, int attempts, Instant expiresAt) {
        PasswordChallenge row = PasswordChallenge.builder()
                .challengeId(UUID.randomUUID()).purpose(PasswordPurpose.RESET).codeHash("0".repeat(64))
                .status(status).attempts(attempts)
                .expiresAt(expiresAt).resendAt(Instant.now())
                .build();
        return repository.saveAndFlush(row).getChallengeId();
    }
}
