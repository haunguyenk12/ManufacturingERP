package com.erp.manufacturing.module.auth.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.RetryAfterException;
import com.erp.manufacturing.common.security.PasswordPolicy;
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
import com.erp.manufacturing.module.auth.dto.VerifyChallengeResponse;
import com.erp.manufacturing.module.auth.repository.PasswordChallengeRepository;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Email-code password flows: first-login setup, forgot-password, and change-with-current-password.
 *
 * <p>Shape of a flow: a 6-digit code is emailed ({@link PasswordChallenge}) → {@link #verify} swaps it
 * for a restricted setup token → {@link #complete} spends the token and sets the password. Neither
 * the code nor the token is stored: the code is kept as an HMAC bound to the challenge id, and the
 * token is recomputed from the same HMAC, so a database dump cannot be replayed.
 *
 * <h3>Idempotency without an Idempotency-Key table</h3>
 * Each step is replay-safe by its own state, which is what the {@code Idempotency-Key} header is
 * for: a repeat of {@code request} inside the resend cooldown returns the live challenge and sends
 * no second mail; a repeat of {@code verify} with the same code returns the same token while it is
 * unspent; a repeat of {@code complete} after success with the same password is a no-op success.
 * {@code change} cannot replay — it revokes the session that carried it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordFlowService {

    static final Duration CODE_TTL        = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    static final Duration SETUP_TOKEN_TTL = Duration.ofMinutes(10);
    static final int      MAX_ATTEMPTS    = 5;
    private static final int MAX_PASSWORD_FAILURES = 5;
    /** Decoy and finished challenges are kept a day for support, then dropped on the next issue. */
    private static final Duration RETENTION = Duration.ofDays(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordChallengeRepository challenges;
    private final UserRepository              users;
    private final PasswordEncoder             passwordEncoder;
    private final EmailNotificationService    email;
    private final TokenStoreService           tokenStore;
    private final AuditLogService             auditLogService;
    private final JwtProperties               jwtProperties;

    // ── Issue ─────────────────────────────────────────────────────────────

    /**
     * Login of a {@code PENDING_SETUP} account whose initial password was correct. A second login
     * inside the cooldown gets the live challenge back instead of another email.
     */
    @Transactional
    public ChallengeResponse beginSetup(User user) {
        return liveChallenge(user, PasswordPurpose.SETUP)
                .map(PasswordFlowService::toResponse)
                .orElseGet(() -> reissue(user, PasswordPurpose.SETUP));
    }

    /** {@code POST /auth/v1/password-challenges}. */
    @Transactional
    public ChallengeResponse request(ChallengeRequest request) {
        return request.purpose() == PasswordPurpose.SETUP ? resendSetup(request) : requestReset(request);
    }

    private ChallengeResponse resendSetup(ChallengeRequest request) {
        if (request.previousId() == null) {
            throw ExceptionFactory.forbidden(AuthErrorCode.PASSWORD_SETUP_REQUIRED);
        }
        PasswordChallenge previous = challenges.findById(request.previousId())
                .filter(c -> c.getPurpose() == PasswordPurpose.SETUP && c.getUserId() != null
                        && c.getStatus() != PasswordChallengeStatus.COMPLETED)
                .orElseThrow(() -> ExceptionFactory.forbidden(AuthErrorCode.PASSWORD_SETUP_REQUIRED));
        User user = users.findById(previous.getUserId())
                .filter(u -> u.getStatus() == UserStatus.PENDING_SETUP && matchesIdentifier(u, request.identifier()))
                .orElseThrow(() -> ExceptionFactory.forbidden(AuthErrorCode.PASSWORD_SETUP_REQUIRED));
        Instant now = Instant.now();
        if (now.isBefore(previous.getResendAt())) {
            throw new RetryAfterException(AuthErrorCode.RESEND_TOO_SOON,
                    Duration.between(now, previous.getResendAt()).toSeconds() + 1);
        }
        challenges.supersede(previous.getChallengeId());
        return issue(user, PasswordPurpose.SETUP);
    }

    /**
     * Same answer whether or not the identifier is a real, eligible account (enumeration
     * prevention): an unknown one gets a decoy challenge that fails verification exactly like a real
     * one with the wrong code. Accounts still awaiting first setup are treated as unknown — the
     * initial password has to be proven first.
     */
    private ChallengeResponse requestReset(ChallengeRequest request) {
        String identifier = normalize(request.identifier());
        User user = users.findByEmail(identifier).or(() -> users.findByUsername(request.identifier().trim()))
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            return decoy(identifier);
        }
        return liveChallenge(user, PasswordPurpose.RESET)
                .map(PasswordFlowService::toResponse)
                .orElseGet(() -> reissue(user, PasswordPurpose.RESET));
    }

    /** A new code replaces the old one: the previous pending code stops working before the new one is mailed. */
    private ChallengeResponse reissue(User user, PasswordPurpose purpose) {
        challenges.findFirstByUserIdAndPurposeAndStatusOrderByCreatedAtDesc(
                        user.getUserId(), purpose, PasswordChallengeStatus.PENDING)
                .ifPresent(old -> challenges.supersede(old.getChallengeId()));
        return issue(user, purpose);
    }

    /** The user's pending challenge, if it was issued so recently that another mail would be spam. */
    private java.util.Optional<PasswordChallenge> liveChallenge(User user, PasswordPurpose purpose) {
        return challenges.findFirstByUserIdAndPurposeAndStatusOrderByCreatedAtDesc(
                        user.getUserId(), purpose, PasswordChallengeStatus.PENDING)
                .filter(c -> Instant.now().isBefore(c.getResendAt()));
    }

    private ChallengeResponse issue(User user, PasswordPurpose purpose) {
        challenges.deleteExpiredBefore(Instant.now().minus(RETENTION));
        UUID id = UUID.randomUUID();
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        PasswordChallenge saved = challenges.save(newChallenge(id, user.getUserId(), purpose, hashCode(id, code)));
        email.sendPasswordCode(user.getEmail(), purpose, code, saved.getExpiresAt());
        // Reconciliation line for QA: ids and times only, never the code.
        log.info("[AUTH] Password code issued: challenge={} user={} purpose={} expiresAt={}",
                id, user.getUsername(), purpose, saved.getExpiresAt());
        return toResponse(saved);
    }

    /**
     * Keyed by the identifier so repeating the request inside the cooldown returns the same id —
     * the behaviour a real account shows. The code hash is random, so nothing can ever match it.
     */
    private ChallengeResponse decoy(String identifier) {
        UUID id = UUID.nameUUIDFromBytes(hmac("decoy", identifier, "").getBytes(StandardCharsets.UTF_8));
        PasswordChallenge existing = challenges.findById(id).orElse(null);
        if (existing != null && Instant.now().isBefore(existing.getResendAt())) {
            return toResponse(existing);
        }
        if (existing != null) {
            challenges.delete(existing);
            challenges.flush();
        }
        byte[] noise = new byte[32];
        RANDOM.nextBytes(noise);
        return toResponse(challenges.save(newChallenge(id, null, PasswordPurpose.RESET, HexFormat.of().formatHex(noise))));
    }

    // ── Verify ────────────────────────────────────────────────────────────

    /** {@code POST /auth/v1/password-challenges/verify}. A wrong code costs an attempt even though the call fails. */
    @Transactional(noRollbackFor = AppException.class)
    public VerifyChallengeResponse verify(VerifyChallengeRequest request) {
        PasswordChallenge c = challenges.findById(request.challengeId())
                .orElseThrow(() -> ExceptionFactory.businessRule(AuthErrorCode.CODE_INVALID));
        Instant now = Instant.now();

        switch (c.getStatus()) {
            case COMPLETED -> throw ExceptionFactory.businessRule(AuthErrorCode.CODE_USED);
            case SUPERSEDED -> throw ExceptionFactory.businessRule(AuthErrorCode.CODE_EXPIRED);
            case VERIFIED -> {
                // Replay of a successful verify: same code, token still unspent → same token.
                if (!codeMatches(c, request.code())) throw ExceptionFactory.businessRule(AuthErrorCode.CODE_USED);
                if (now.isAfter(c.getVerifiedAt().plus(SETUP_TOKEN_TTL))) {
                    throw ExceptionFactory.businessRule(AuthErrorCode.CODE_EXPIRED);
                }
                return new VerifyChallengeResponse(setupToken(c));
            }
            case PENDING -> { /* checked below */ }
        }
        if (!now.isBefore(c.getExpiresAt())) {
            throw ExceptionFactory.businessRule(AuthErrorCode.CODE_EXPIRED);
        }
        if (c.getAttempts() >= MAX_ATTEMPTS) {
            throw new RetryAfterException(AuthErrorCode.CODE_ATTEMPTS, Duration.between(now, c.getExpiresAt()).toSeconds() + 1);
        }
        if (!codeMatches(c, request.code())) {
            int attemptsBefore = c.getAttempts();
            challenges.incrementAttempts(c.getChallengeId());
            if (attemptsBefore + 1 >= MAX_ATTEMPTS) {
                throw new RetryAfterException(AuthErrorCode.CODE_ATTEMPTS, Duration.between(now, c.getExpiresAt()).toSeconds() + 1);
            }
            throw ExceptionFactory.businessRule(AuthErrorCode.CODE_INVALID);
        }
        if (challenges.markVerified(c.getChallengeId(), now, MAX_ATTEMPTS) != 1) {
            // Lost a race with a concurrent verify of the same code: the winner holds the token.
            throw ExceptionFactory.businessRule(AuthErrorCode.CODE_USED);
        }
        return new VerifyChallengeResponse(setupToken(c));
    }

    // ── Complete ──────────────────────────────────────────────────────────

    /**
     * {@code POST /auth/v1/password-challenges/complete}. The policy is checked <em>before</em> the
     * token is spent, so a rejected password leaves the token usable for another try.
     */
    @Transactional
    public void complete(CompleteChallengeRequest request, String ip, String traceId) {
        PasswordChallenge c = resolveToken(request.setupToken());
        User user = users.findById(c.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE || u.getStatus() == UserStatus.PENDING_SETUP)
                .orElseThrow(() -> ExceptionFactory.businessRule(AuthErrorCode.SETUP_TOKEN_INVALID));

        if (c.getStatus() == PasswordChallengeStatus.COMPLETED) {
            // Replay after success: harmless only if it carries the password that is now in force.
            if (passwordEncoder.matches(request.password(), user.getPassword())) return;
            throw ExceptionFactory.businessRule(AuthErrorCode.SETUP_TOKEN_INVALID);
        }
        if (c.getStatus() != PasswordChallengeStatus.VERIFIED
                || Instant.now().isAfter(c.getVerifiedAt().plus(SETUP_TOKEN_TTL))) {
            throw ExceptionFactory.businessRule(AuthErrorCode.SETUP_TOKEN_INVALID);
        }

        PasswordPolicy.require(request.password());
        if (passwordEncoder.matches(request.password(), user.getPassword())) {
            throw PasswordPolicy.sameAsCurrent();
        }
        if (challenges.markCompleted(c.getChallengeId(), Instant.now()) != 1) {
            throw ExceptionFactory.businessRule(AuthErrorCode.SETUP_TOKEN_INVALID);
        }

        boolean firstSetup = user.getStatus() == UserStatus.PENDING_SETUP;
        user.setPassword(passwordEncoder.encode(request.password()));
        if (firstSetup) user.activate();
        endAllSessions(user);
        auditLogService.logAuth(user.getUserId(), user.getUsername(), ip, traceId,
                c.getPurpose() == PasswordPurpose.RESET ? AuditAction.PASSWORD_RESET : AuditAction.PASSWORD_CHANGED,
                firstSetup ? "Initial password set via email code" : "Password reset via email code");
        log.info("[AUTH] Password set via email code: user={} purpose={} firstSetup={}",
                user.getUsername(), c.getPurpose(), firstSetup);
    }

    // ── Change ────────────────────────────────────────────────────────────

    /** {@code POST /auth/v1/password/change}: the caller's own session plus their current password. */
    @Transactional(noRollbackFor = AppException.class)
    public void change(String username, ChangePasswordRequest request, String ip, String traceId) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> ExceptionFactory.unauthorized(AuthErrorCode.AUTHENTICATION_REQUIRED));
        // Shares the login counter: a stolen session must not get unlimited guesses at the password.
        if (tokenStore.getFailCount(username) >= MAX_PASSWORD_FAILURES) {
            throw ExceptionFactory.unauthorized(AuthErrorCode.ACCOUNT_LOCKED,
                    "Too many failed attempts. Try again in 15 minutes.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            tokenStore.incrementFailCount(username);
            throw ExceptionFactory.businessRule(AuthErrorCode.CURRENT_PASSWORD_INVALID);
        }
        PasswordPolicy.require(request.password());
        if (passwordEncoder.matches(request.password(), user.getPassword())) {
            throw PasswordPolicy.sameAsCurrent();
        }
        tokenStore.resetFailCount(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        endAllSessions(user);
        auditLogService.logAuth(user.getUserId(), username, ip, traceId,
                AuditAction.PASSWORD_CHANGED, "Password changed with current password");
        log.info("[AUTH] Password changed: user={}", username);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Old tokens die with the old password; the user logs in again. */
    private void endAllSessions(User user) {
        user.revokeAllSessions();
        users.save(user);
        tokenStore.deleteAllUserTokens(user.getUserId());
        tokenStore.deleteAllDeviceSessions(user.getUserId());
    }

    private PasswordChallenge resolveToken(String token) {
        int dot = token.indexOf('.');
        if (dot < 1) throw ExceptionFactory.businessRule(AuthErrorCode.SETUP_TOKEN_INVALID);
        UUID id;
        try {
            id = UUID.fromString(token.substring(0, dot));
        } catch (IllegalArgumentException e) {
            throw ExceptionFactory.businessRule(AuthErrorCode.SETUP_TOKEN_INVALID);
        }
        PasswordChallenge c = challenges.findById(id)
                .filter(found -> found.getUserId() != null)
                .orElseThrow(() -> ExceptionFactory.businessRule(AuthErrorCode.SETUP_TOKEN_INVALID));
        if (!MessageDigest.isEqual(setupToken(c).getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw ExceptionFactory.businessRule(AuthErrorCode.SETUP_TOKEN_INVALID);
        }
        return c;
    }

    private PasswordChallenge newChallenge(UUID id, UUID userId, PasswordPurpose purpose, String codeHash) {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return PasswordChallenge.builder()
                .challengeId(id).userId(userId).purpose(purpose).codeHash(codeHash)
                .expiresAt(now.plus(CODE_TTL)).resendAt(now.plus(RESEND_COOLDOWN))
                .build();
    }

    private static ChallengeResponse toResponse(PasswordChallenge c) {
        return new ChallengeResponse(c.getChallengeId(), c.getExpiresAt(), c.getResendAt());
    }

    private static boolean matchesIdentifier(User user, String identifier) {
        return user.getUsername().equals(identifier.trim()) || user.getEmail().equalsIgnoreCase(identifier.trim());
    }

    private static String normalize(String identifier) {
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private boolean codeMatches(PasswordChallenge c, String code) {
        return MessageDigest.isEqual(hashCode(c.getChallengeId(), code.trim()).getBytes(StandardCharsets.UTF_8),
                c.getCodeHash().getBytes(StandardCharsets.UTF_8));
    }

    private String hashCode(UUID challengeId, String code) {
        return hmac("code", challengeId.toString(), code);
    }

    /** {@code <challengeId>.<mac>}; the mac covers the stored code hash, so a new code can never reuse an old token. */
    private String setupToken(PasswordChallenge c) {
        return c.getChallengeId() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                hmac("setup-token", c.getChallengeId().toString(), c.getCodeHash()).getBytes(StandardCharsets.UTF_8));
    }

    /** HMAC-SHA256 keyed by the server secret, domain-separated by {@code label}; hex-encoded. */
    private String hmac(String label, String a, String b) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((label + '\0' + a + '\0' + b).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is required by the Java platform", e);
        }
    }
}
