package com.erp.manufacturing.module.auth.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ErrorCode;
import com.erp.manufacturing.common.exception.RetryAfterException;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.JwtProperties;
import com.erp.manufacturing.module.auth.domain.PasswordChallenge;
import com.erp.manufacturing.module.auth.domain.PasswordChallengeStatus;
import com.erp.manufacturing.module.auth.domain.PasswordPurpose;
import com.erp.manufacturing.module.auth.dto.ChallengeRequest;
import com.erp.manufacturing.module.auth.dto.ChallengeResponse;
import com.erp.manufacturing.module.auth.dto.ChangePasswordRequest;
import com.erp.manufacturing.module.auth.dto.CompleteChallengeRequest;
import com.erp.manufacturing.module.auth.dto.VerifyChallengeRequest;
import com.erp.manufacturing.module.auth.repository.PasswordChallengeRepository;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PasswordFlowService} against an in-memory challenge table: the guarded UPDATEs are
 * re-implemented below with the same conditions as {@code PasswordChallengeRepository}, and the real
 * HMAC runs, so these cases exercise the actual code → token → password chain. The UPDATE
 * conditions themselves are pinned against Postgres in {@code PasswordChallengeRepositoryIT}.
 */
@DisplayName("PasswordFlowService")
class PasswordFlowServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-unit-test-secret";
    private static final String STRONG = "Str0ng!Passw0rd";

    private final Map<UUID, PasswordChallenge> table = new HashMap<>();
    private final PasswordChallengeRepository challenges = mock(PasswordChallengeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final EmailNotificationService email = mock(EmailNotificationService.class);
    private final TokenStoreService tokenStore = mock(TokenStoreService.class);
    private final AuditLogService audit = mock(AuditLogService.class);

    private PasswordFlowService service;
    private User pending;

    @BeforeEach
    void setUp() {
        service = new PasswordFlowService(challenges, users, encoder, email, tokenStore, audit,
                new JwtProperties(SECRET, 900_000L, 604_800_000L, 2_592_000_000L));
        pending = User.builder().userId(UUID.randomUUID()).username("new.hire").email("new.hire@omniplant.vn")
                .password("{initial-hash}").status(UserStatus.PENDING_SETUP).build();

        when(challenges.save(any(PasswordChallenge.class))).thenAnswer(inv -> {
            PasswordChallenge c = inv.getArgument(0);
            table.put(c.getChallengeId(), c);
            return c;
        });
        when(challenges.findById(any(UUID.class))).thenAnswer(inv -> Optional.ofNullable(table.get(inv.<UUID>getArgument(0))));
        when(challenges.findFirstByUserIdAndPurposeAndStatusOrderByCreatedAtDesc(any(), any(), any()))
                .thenAnswer(inv -> table.values().stream()
                        .filter(c -> inv.getArgument(0).equals(c.getUserId()) && c.getPurpose() == inv.getArgument(1)
                                && c.getStatus() == inv.getArgument(2))
                        .findFirst());
        when(challenges.incrementAttempts(any(UUID.class))).thenAnswer(inv -> {
            table.get(inv.<UUID>getArgument(0)).setAttempts(table.get(inv.<UUID>getArgument(0)).getAttempts() + 1);
            return 1;
        });
        when(challenges.markVerified(any(UUID.class), any(Instant.class), anyInt())).thenAnswer(inv -> {
            PasswordChallenge c = table.get(inv.<UUID>getArgument(0));
            if (c.getStatus() != PasswordChallengeStatus.PENDING) return 0;
            c.setStatus(PasswordChallengeStatus.VERIFIED);
            c.setVerifiedAt(inv.getArgument(1));
            return 1;
        });
        when(challenges.markCompleted(any(UUID.class), any(Instant.class))).thenAnswer(inv -> {
            PasswordChallenge c = table.get(inv.<UUID>getArgument(0));
            if (c.getStatus() != PasswordChallengeStatus.VERIFIED) return 0;
            c.setStatus(PasswordChallengeStatus.COMPLETED);
            return 1;
        });
        when(challenges.supersede(any(UUID.class))).thenAnswer(inv -> {
            table.get(inv.<UUID>getArgument(0)).setStatus(PasswordChallengeStatus.SUPERSEDED);
            return 1;
        });
        when(users.findById(pending.getUserId())).thenReturn(Optional.of(pending));
        when(encoder.encode(any())).thenAnswer(inv -> "{hash}" + inv.getArgument(0));
        when(encoder.matches(any(), any())).thenAnswer(inv -> ("{hash}" + inv.getArgument(0)).equals(inv.getArgument(1))
                || ("{initial-hash}".equals(inv.getArgument(1)) && "Init1al!Pass".equals(inv.getArgument(0))));
    }

    // ── issue ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("beginSetup: emails a 6-digit code once and never stores it")
    void beginSetup_sendsSixDigitCodeAndStoresOnlyItsHash() {
        ChallengeResponse challenge = service.beginSetup(pending);

        String code = sentCode();
        assertThat(code).matches("\\d{6}");
        PasswordChallenge row = table.get(challenge.id());
        assertThat(row.getCodeHash()).isNotEqualTo(code).doesNotContain(code);
        assertThat(row.getPurpose()).isEqualTo(PasswordPurpose.SETUP);
        assertThat(row.getExpiresAt()).isAfter(Instant.now().plusSeconds(9 * 60));
        assertThat(challenge.resendAt()).isBefore(challenge.expiresAt());
        verify(email).sendPasswordCode(eq("new.hire@omniplant.vn"), eq(PasswordPurpose.SETUP), any(), eq(row.getExpiresAt()));
    }

    @Test
    @DisplayName("beginSetup: a second login inside the cooldown returns the live challenge and sends no second email")
    void beginSetup_repeatInsideCooldown_reusesChallenge() {
        ChallengeResponse first = service.beginSetup(pending);
        ChallengeResponse second = service.beginSetup(pending);

        assertThat(second.id()).isEqualTo(first.id());
        verify(email, times(1)).sendPasswordCode(any(), any(), any(), any());
    }

    @Test
    @DisplayName("beginSetup: a login after the cooldown mails a new code and the old code stops working")
    void beginSetup_repeatAfterCooldown_supersedesPreviousCode() {
        ChallengeResponse first = service.beginSetup(pending);
        String oldCode = sentCode();
        table.get(first.id()).setResendAt(Instant.now().minusSeconds(1));

        ChallengeResponse second = service.beginSetup(pending);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(table.get(first.id()).getStatus()).isEqualTo(PasswordChallengeStatus.SUPERSEDED);
        assertThat(code(() -> service.verify(new VerifyChallengeRequest(first.id(), oldCode)))).isEqualTo(AuthErrorCode.CODE_EXPIRED);
        verify(email, times(2)).sendPasswordCode(any(), any(), any(), any());
    }

    @Test
    @DisplayName("resend: before resendAt → 429 RESEND_TOO_SOON with a positive Retry-After, no email")
    void resend_tooSoon_isRefusedWithRetryAfter() {
        ChallengeResponse first = service.beginSetup(pending);

        assertThatThrownBy(() -> service.request(new ChallengeRequest("new.hire", PasswordPurpose.SETUP, first.id())))
                .isInstanceOfSatisfying(RetryAfterException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.RESEND_TOO_SOON);
                    assertThat(ex.getRetryAfterSeconds()).isBetween(1L, 31L);
                });
        verify(email, times(1)).sendPasswordCode(any(), any(), any(), any());
    }

    @Test
    @DisplayName("resend: after resendAt the old code stops working and a new challenge is mailed")
    void resend_afterCooldown_supersedesPreviousCode() {
        ChallengeResponse first = service.beginSetup(pending);
        String oldCode = sentCode();
        table.get(first.id()).setResendAt(Instant.now().minusSeconds(1));

        ChallengeResponse second = service.request(new ChallengeRequest("new.hire@omniplant.vn", PasswordPurpose.SETUP, first.id()));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(table.get(first.id()).getStatus()).isEqualTo(PasswordChallengeStatus.SUPERSEDED);
        assertThat(code(() -> service.verify(new VerifyChallengeRequest(first.id(), oldCode)))).isEqualTo(AuthErrorCode.CODE_EXPIRED);
        verify(email, times(2)).sendPasswordCode(any(), any(), any(), any());
    }

    @Test
    @DisplayName("resend: no previousId, or a previousId that is not this account's challenge → 403 PASSWORD_SETUP_REQUIRED")
    void resend_withoutProofOfInitialLogin_isForbidden() {
        service.beginSetup(pending);

        assertThat(code(() -> service.request(new ChallengeRequest("new.hire", PasswordPurpose.SETUP, null))))
                .isEqualTo(AuthErrorCode.PASSWORD_SETUP_REQUIRED);
        assertThat(code(() -> service.request(new ChallengeRequest("new.hire", PasswordPurpose.SETUP, UUID.randomUUID()))))
                .isEqualTo(AuthErrorCode.PASSWORD_SETUP_REQUIRED);
        assertThat(code(() -> service.request(new ChallengeRequest("someone.else", PasswordPurpose.SETUP, only().getChallengeId()))))
                .isEqualTo(AuthErrorCode.PASSWORD_SETUP_REQUIRED);
    }

    @Test
    @DisplayName("RESET for an unknown identifier looks identical to a real one and sends nothing")
    void reset_unknownIdentifier_getsDecoyThatFailsLikeAWrongCode() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(users.findByUsername(any())).thenReturn(Optional.empty());

        ChallengeResponse decoy = service.request(new ChallengeRequest("ghost@nowhere.vn", PasswordPurpose.RESET, null));
        ChallengeResponse again = service.request(new ChallengeRequest("ghost@nowhere.vn", PasswordPurpose.RESET, null));

        assertThat(again.id()).isEqualTo(decoy.id());
        assertThat(decoy.expiresAt()).isAfter(decoy.resendAt());
        verifyNoInteractions(email);
        assertThat(code(() -> service.verify(new VerifyChallengeRequest(decoy.id(), "123456")))).isEqualTo(AuthErrorCode.CODE_INVALID);
    }

    @Test
    @DisplayName("RESET for an account still awaiting setup is treated as unknown: the initial password must be proven first")
    void reset_pendingSetupAccount_getsDecoy() {
        when(users.findByEmail("new.hire@omniplant.vn")).thenReturn(Optional.of(pending));

        service.request(new ChallengeRequest("new.hire@omniplant.vn", PasswordPurpose.RESET, null));

        verifyNoInteractions(email);
    }

    @Test
    @DisplayName("RESET for an ACTIVE account mails a RESET code")
    void reset_activeAccount_sendsCode() {
        pending.setStatus(UserStatus.ACTIVE);
        when(users.findByEmail("new.hire@omniplant.vn")).thenReturn(Optional.of(pending));

        service.request(new ChallengeRequest("New.Hire@OmniPlant.vn", PasswordPurpose.RESET, null));

        verify(email).sendPasswordCode(eq("new.hire@omniplant.vn"), eq(PasswordPurpose.RESET), any(), any());
    }

    // ── verify ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("verify: wrong code → CODE_INVALID and the attempt is counted")
    void verify_wrongCode_countsAnAttempt() {
        ChallengeResponse challenge = service.beginSetup(pending);
        String wrong = sentCode().equals("000000") ? "000001" : "000000";

        assertThat(code(() -> service.verify(new VerifyChallengeRequest(challenge.id(), wrong)))).isEqualTo(AuthErrorCode.CODE_INVALID);

        assertThat(table.get(challenge.id()).getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("verify: the fifth wrong code → 429 CODE_ATTEMPTS, and the right code no longer works")
    void verify_fiveWrongCodes_lockTheChallenge() {
        ChallengeResponse challenge = service.beginSetup(pending);
        String right = sentCode();
        String wrong = right.equals("000000") ? "000001" : "000000";

        for (int i = 0; i < 4; i++) {
            assertThat(code(() -> service.verify(new VerifyChallengeRequest(challenge.id(), wrong)))).isEqualTo(AuthErrorCode.CODE_INVALID);
        }
        assertThat(code(() -> service.verify(new VerifyChallengeRequest(challenge.id(), wrong)))).isEqualTo(AuthErrorCode.CODE_ATTEMPTS);
        assertThat(code(() -> service.verify(new VerifyChallengeRequest(challenge.id(), right)))).isEqualTo(AuthErrorCode.CODE_ATTEMPTS);
        verify(challenges, never()).markVerified(any(), any(), anyInt());
    }

    @Test
    @DisplayName("verify: expired challenge → CODE_EXPIRED even with the right code")
    void verify_expired_isRefused() {
        ChallengeResponse challenge = service.beginSetup(pending);
        String right = sentCode();
        PasswordChallenge row = table.get(challenge.id());
        row.setExpiresAt(Instant.now().minusSeconds(1));

        assertThat(code(() -> service.verify(new VerifyChallengeRequest(challenge.id(), right)))).isEqualTo(AuthErrorCode.CODE_EXPIRED);
    }

    @Test
    @DisplayName("verify: unknown challenge id → CODE_INVALID, not a hint that the id never existed")
    void verify_unknownChallenge_isIndistinguishableFromAWrongCode() {
        assertThat(code(() -> service.verify(new VerifyChallengeRequest(UUID.randomUUID(), "123456")))).isEqualTo(AuthErrorCode.CODE_INVALID);
    }

    @Test
    @DisplayName("verify: replaying the same code returns the same token; a different code is CODE_USED")
    void verify_replay_returnsSameToken() {
        ChallengeResponse challenge = service.beginSetup(pending);
        String right = sentCode();

        String token = service.verify(new VerifyChallengeRequest(challenge.id(), right)).setupToken();

        assertThat(service.verify(new VerifyChallengeRequest(challenge.id(), right)).setupToken()).isEqualTo(token);
        assertThat(code(() -> service.verify(new VerifyChallengeRequest(challenge.id(), "999999")))).isEqualTo(AuthErrorCode.CODE_USED);
    }

    // ── complete ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("complete: sets the password, activates the account, revokes every session, audits it")
    void complete_firstSetup_activatesAndEndsSessions() {
        String token = verifiedToken();

        service.complete(new CompleteChallengeRequest(token, STRONG), "10.0.0.1", "trace");

        assertThat(pending.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(pending.getPassword()).isEqualTo("{hash}" + STRONG);
        assertThat(pending.getAuthVersion()).isEqualTo(1L);
        verify(tokenStore).deleteAllUserTokens(pending.getUserId());
        verify(tokenStore).deleteAllDeviceSessions(pending.getUserId());
        verify(audit).logAuth(eq(pending.getUserId()), eq("new.hire"), eq("10.0.0.1"), eq("trace"),
                eq(AuditAction.PASSWORD_CHANGED), any());
    }

    @Test
    @DisplayName("complete: a weak password is rejected BEFORE the token is spent, so the user can retry with the same token")
    void complete_weakPassword_leavesTokenUnspent() {
        String token = verifiedToken();

        assertThat(code(() -> service.complete(new CompleteChallengeRequest(token, "weak"), "ip", "t")))
                .isEqualTo(AuthErrorCode.PASSWORD_POLICY);

        verify(challenges, never()).markCompleted(any(), any());
        assertThat(pending.getStatus()).isEqualTo(UserStatus.PENDING_SETUP);
        service.complete(new CompleteChallengeRequest(token, STRONG), "ip", "t");
        assertThat(pending.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("complete: the new password must differ from the initial one")
    void complete_sameAsInitialPassword_isRejected() {
        String token = verifiedToken();

        assertThat(code(() -> service.complete(new CompleteChallengeRequest(token, "Init1al!Pass"), "ip", "t")))
                .isEqualTo(AuthErrorCode.PASSWORD_POLICY);

        assertThat(pending.getPassword()).isEqualTo("{initial-hash}");
        verify(challenges, never()).markCompleted(any(), any());
    }

    @Test
    @DisplayName("complete: replay after success with the same password is a no-op success; with another password it is refused")
    void complete_replay_isIdempotent() {
        String token = verifiedToken();
        service.complete(new CompleteChallengeRequest(token, STRONG), "ip", "t");

        service.complete(new CompleteChallengeRequest(token, STRONG), "ip", "t");

        verify(tokenStore, times(1)).deleteAllUserTokens(pending.getUserId());
        assertThat(code(() -> service.complete(new CompleteChallengeRequest(token, "An0ther!Pass1"), "ip", "t")))
                .isEqualTo(AuthErrorCode.SETUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("complete: forged, truncated, or wrong-challenge tokens → SETUP_TOKEN_INVALID")
    void complete_badTokens_areRefused() {
        String token = verifiedToken();
        String forged = token.substring(0, token.length() - 2) + "xx";
        String otherId = UUID.randomUUID() + token.substring(token.indexOf('.'));

        for (String bad : new String[] {forged, otherId, "garbage", "not-a-uuid.abc", ".abc", token + "x"}) {
            assertThat(code(() -> service.complete(new CompleteChallengeRequest(bad, STRONG), "ip", "t")))
                    .as(bad).isEqualTo(AuthErrorCode.SETUP_TOKEN_INVALID);
        }
        assertThat(pending.getStatus()).isEqualTo(UserStatus.PENDING_SETUP);
    }

    @Test
    @DisplayName("complete: a token that was issued for a code which was later superseded is dead")
    void complete_tokenOfSupersededChallenge_isRefused() {
        String token = verifiedToken();
        table.get(UUID.fromString(token.substring(0, token.indexOf('.')))).setStatus(PasswordChallengeStatus.SUPERSEDED);

        assertThat(code(() -> service.complete(new CompleteChallengeRequest(token, STRONG), "ip", "t")))
                .isEqualTo(AuthErrorCode.SETUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("complete: a locked account cannot use a token")
    void complete_lockedAccount_isRefused() {
        String token = verifiedToken();
        pending.setStatus(UserStatus.LOCKED);

        assertThat(code(() -> service.complete(new CompleteChallengeRequest(token, STRONG), "ip", "t")))
                .isEqualTo(AuthErrorCode.SETUP_TOKEN_INVALID);
    }

    // ── change ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("change: wrong current password → CURRENT_PASSWORD_INVALID (422, not 401) and the failure is counted")
    void change_wrongCurrentPassword_isCounted() {
        User active = activeUser();

        assertThat(code(() -> service.change("old.hand", new ChangePasswordRequest("nope", STRONG), "ip", "t")))
                .isEqualTo(AuthErrorCode.CURRENT_PASSWORD_INVALID);

        assertThat(AuthErrorCode.CURRENT_PASSWORD_INVALID.status().value()).isEqualTo(422);
        verify(tokenStore).incrementFailCount("old.hand");
        assertThat(active.getPassword()).isEqualTo("{hash}Curr3nt!Pass");
    }

    @Test
    @DisplayName("change: right current password sets the new one and revokes every session")
    void change_success_endsAllSessions() {
        User active = activeUser();

        service.change("old.hand", new ChangePasswordRequest("Curr3nt!Pass", STRONG), "ip", "t");

        assertThat(active.getPassword()).isEqualTo("{hash}" + STRONG);
        assertThat(active.getAuthVersion()).isEqualTo(1L);
        verify(tokenStore).deleteAllUserTokens(active.getUserId());
        verify(tokenStore).resetFailCount("old.hand");
        verify(audit).logAuth(eq(active.getUserId()), eq("old.hand"), eq("ip"), eq("t"), eq(AuditAction.PASSWORD_CHANGED), any());
    }

    @Test
    @DisplayName("change: new password equal to the current one or failing policy → PASSWORD_POLICY, nothing changes")
    void change_policyViolations_changeNothing() {
        User active = activeUser();

        assertThat(code(() -> service.change("old.hand", new ChangePasswordRequest("Curr3nt!Pass", "Curr3nt!Pass"), "ip", "t")))
                .isEqualTo(AuthErrorCode.PASSWORD_POLICY);
        assertThat(code(() -> service.change("old.hand", new ChangePasswordRequest("Curr3nt!Pass", "short"), "ip", "t")))
                .isEqualTo(AuthErrorCode.PASSWORD_POLICY);

        assertThat(active.getAuthVersion()).isZero();
        verify(tokenStore, never()).deleteAllUserTokens(any());
    }

    @Test
    @DisplayName("change: after five failures the account is locked out of guessing")
    void change_afterFiveFailures_isLocked() {
        activeUser();
        when(tokenStore.getFailCount("old.hand")).thenReturn(5L);

        assertThat(code(() -> service.change("old.hand", new ChangePasswordRequest("Curr3nt!Pass", STRONG), "ip", "t")))
                .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** Drives beginSetup → verify and returns the setup token. */
    private String verifiedToken() {
        ChallengeResponse challenge = service.beginSetup(pending);
        return service.verify(new VerifyChallengeRequest(challenge.id(), sentCode())).setupToken();
    }

    private String sentCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(email, org.mockito.Mockito.atLeastOnce()).sendPasswordCode(any(), any(), code.capture(), any());
        return code.getValue();
    }

    private PasswordChallenge only() {
        return table.values().iterator().next();
    }

    private User activeUser() {
        User u = User.builder().userId(UUID.randomUUID()).username("old.hand").email("old.hand@omniplant.vn")
                .password("{hash}Curr3nt!Pass").status(UserStatus.ACTIVE).build();
        when(users.findByUsername("old.hand")).thenReturn(Optional.of(u));
        return u;
    }

    private static ErrorCode code(Runnable call) {
        try {
            call.run();
        } catch (AppException e) {
            return e.getErrorCode();
        }
        throw new AssertionError("expected an AppException");
    }
}
