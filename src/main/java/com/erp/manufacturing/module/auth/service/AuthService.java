package com.erp.manufacturing.module.auth.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.audit.Auditable;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.JwtProperties;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.auth.dto.AuthResponse;
import com.erp.manufacturing.module.auth.dto.ForgotPasswordRequest;
import com.erp.manufacturing.module.auth.dto.LoginRequest;
import com.erp.manufacturing.module.auth.dto.LogoutRequest;
import com.erp.manufacturing.module.auth.dto.MeResponse;
import com.erp.manufacturing.module.auth.dto.RefreshRequest;
import com.erp.manufacturing.module.auth.dto.ResetPasswordRequest;
import com.erp.manufacturing.module.organization.service.AccessControlService;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import com.erp.manufacturing.module.user.repository.UserRepository;
import com.erp.manufacturing.module.user.service.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authentication service handling login, logout, and token refresh.
 *
 * <h3>Key behaviours:</h3>
 * <ul>
 *   <li><b>Brute-force protection</b> – max 5 failures in 15 min → temporary lockout</li>
 *   <li><b>Single-device session</b> – Enforced single-session per user. A user can only be
 *       logged in from one device at a time. Logging in from a new device invalidates all prior sessions.
 *       If {@code deviceId} is not provided by the client, one is derived server-side
 *       from a hash of {@code User-Agent + IP}.</li>
 *   <li><b>Token rotation on refresh</b> – new pair saved, old tokenId marked "used", then deleted</li>
 *   <li><b>Reuse detection (RTR)</b> – a tokenId replayed after being rotated away revokes every
 *       session of that user and returns {@code TOKEN_REUSE_DETECTED} (B80)</li>
 *   <li><b>Refresh with expired access token</b> – the filter allows expired tokens through
 *       on the refresh path; this service validates the opaque refresh token independently</li>
 *   <li><b>Audit logging</b> – all auth events recorded</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int MAX_FAIL_ATTEMPTS = 5;

    private static final long CONCURRENT_REFRESH_WAIT_MS = 150L;

    /** Bounded wait paid only when {@code acquireRefreshLock} loses the race — see {@code sleepBriefly}. */
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtTokenProvider       jwtTokenProvider;
    private final TokenStoreService      tokenStore;
    private final PasswordEncoder        passwordEncoder;
    private final AuditLogService        auditLogService;
    private final JwtProperties          jwtProperties;
    private final AccessControlService   accessControlService;
    private final UserRepository         userRepository;
    private final PasswordResetTokenService passwordResetTokenService;
    private final EmailNotificationService  emailNotificationService;

    // ── Login ─────────────────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip      = (String) httpRequest.getAttribute("clientIp");
        String traceId = (String) httpRequest.getAttribute("traceId");

        // Brute-force check
        long failCount = tokenStore.getFailCount(request.username());
        if (failCount >= MAX_FAIL_ATTEMPTS) {
            auditLogService.logAuthFailure(request.username(), ip, traceId,
                    AuditAction.ACCOUNT_LOCKED, "Account locked after " + MAX_FAIL_ATTEMPTS + " failed attempts");
            throw ExceptionFactory.unauthorized(AuthErrorCode.ACCOUNT_LOCKED,
                    "Too many failed attempts. Try again in 15 minutes.");
        }

        // Load user and validate password
        UserPrincipal principal;
        try {
            principal = (UserPrincipal) userDetailsService.loadUserByUsername(request.username());
        } catch (Exception e) {
            incrementFailAndAudit(request.username(), ip, traceId);
            throw ExceptionFactory.unauthorized(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.password(), principal.getPassword())) {
            incrementFailAndAudit(request.username(), ip, traceId);
            throw ExceptionFactory.unauthorized(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (!principal.isEnabled()) {
            throw ExceptionFactory.unauthorized(AuthErrorCode.ACCOUNT_INACTIVE,
                    "Account is locked or inactive.");
        }

        // Clear fail counter on success
        tokenStore.resetFailCount(request.username());

        // A successful single-session login invalidates every access token from the prior session.
        User loginUser = userRepository.findById(principal.getUserId()).orElse(null);
        if (loginUser != null) {
            loginUser.revokeAllSessions();
            userRepository.save(loginUser);
            principal = (UserPrincipal) userDetailsService.loadUserByUsername(request.username());
        }

        // Clear all previous device sessions and refresh tokens to enforce single-session per user
        tokenStore.deleteAllUserTokens(principal.getUserId());
        tokenStore.deleteAllDeviceSessions(principal.getUserId());

        // Resolve deviceId: use client-provided value or derive from UA + IP
        String deviceId = resolveDeviceId(request.deviceId(), httpRequest, ip);

        // Register device session
        tokenStore.saveDeviceSession(principal.getUserId(), deviceId, ip);

        // Issue token pair
        String accessToken  = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken();
        String tokenId      = UUID.randomUUID().toString();

        tokenStore.saveRefreshToken(principal.getUserId(), tokenId, refreshToken, principal.getAuthVersion());
        // B81: the absolute timeout is measured from here and is never extended by a refresh.
        tokenStore.saveSessionStart(principal.getUserId(), tokenId, Instant.now());

        auditLogService.logAuth(principal.getUserId(), principal.getUsername(), ip, traceId,
                AuditAction.LOGIN, "Login from " + ip + " device=" + deviceId);
        log.info("[AUTH] Login success: user={} ip={} deviceId={}", principal.getUsername(), ip, deviceId);

        return new AuthResponse(
                accessToken, refreshToken, tokenId,
                jwtProperties.accessTokenExpiryMs() / 1000,
                deviceId,
                false);
    }

    // ── Refresh ───────────────────────────────────────────────────────────

    /**
     * Issues a new token pair using the opaque refresh token.
     *
     * <p>Identity is resolved from {@code request.tokenId()} alone via {@link
     * TokenStoreService#getTokenOwner} (P0 auth fix) — this endpoint no longer needs an {@code
     * Authorization} header at all, matching its {@code permitAll} contract. Security itself is
     * enforced by the opaque refresh token + tokenId pair stored in Redis, unchanged.
     */
    public AuthResponse refresh(RefreshRequest request, HttpServletRequest httpRequest) {
        String ip      = (String) httpRequest.getAttribute("clientIp");
        String traceId = (String) httpRequest.getAttribute("traceId");

        UUID userId = tokenStore.getTokenOwner(request.tokenId());
        if (userId == null) {
            throw ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED));
        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(user.getUsername());
        if (!principal.isEnabled()) {
            throw ExceptionFactory.unauthorized(AuthErrorCode.ACCOUNT_INACTIVE);
        }

        // Concurrent refresh race: if another request is mid-rotation for this exact tokenId right
        // now (client retry, double-fire, etc.), give it a brief head start to finish and publish
        // its result before we read the token store below — see the stored == null branch.
        if (!tokenStore.acquireRefreshLock(request.tokenId())) {
            sleepBriefly();
        }

        // Validate opaque refresh token
        String stored = tokenStore.getRefreshToken(principal.getUserId(), request.tokenId());
        if (stored == null) {
            // Concurrent refresh race: this tokenId may have just been rotated by a duplicate of
            // THIS SAME request rather than an attacker replaying a stolen token. Hand back the one
            // resulting pair instead of treating a legitimate caller as a thief.
            // RTR (B80): the tokenId is gone from the store, but if we rotated it away moments ago
            // then someone is replaying a token they should no longer hold — treat it as stolen.
            if (tokenStore.wasRefreshTokenUsed(request.tokenId(), request.refreshToken(),
                    principal.getAuthVersion())) {
                revokeAllSessions(user);
                auditLogService.logAuthFailure(principal.getUsername(), ip, traceId,
                        AuditAction.SUSPICIOUS_TOKEN_REUSE,
                        "Reused refresh tokenId=" + request.tokenId() + "; all sessions revoked");
                log.warn("[AUTH] Refresh token reuse detected: user={} tokenId={} ip={}",
                        principal.getUsername(), request.tokenId(), ip);
                throw ExceptionFactory.unauthorized(AuthErrorCode.TOKEN_REUSE_DETECTED);
            }
            throw ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        if (!tokenStore.matchesRefreshToken(principal.getUserId(), request.tokenId(),
                request.refreshToken(), principal.getAuthVersion())) {
            // Deliberately NOT an RTR case (B80): the tokenId still exists, so it was never rotated
            // away — this is a wrong/tampered token value, not a replay.
            throw ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // Absolute session timeout (B81). Placed here on purpose: after the token has been proven
        // valid, so a caller holding nothing learns nothing about session age; and before rotation,
        // so a session past its absolute limit can never walk away with a fresh pair.
        Instant sessionStart = tokenStore.getSessionStart(principal.getUserId(), request.tokenId());
        if (sessionStart == null) {
            // Session established before D8b shipped: it has no start stamp to judge. Treated as
            // starting now rather than as expired — refresh TTL is 7 days, so every live session
            // carries a stamp within a week, whereas failing closed would log out every signed-in
            // user the moment this deploys, buying no security.
            sessionStart = Instant.now();
        } else if (Duration.between(sessionStart, Instant.now()).toMillis()
                >= jwtProperties.absoluteSessionTimeoutMs()) {
            revokeAllSessions(user);
            auditLogService.logAuthFailure(principal.getUsername(), ip, traceId,
                    AuditAction.SESSION_ABSOLUTE_TIMEOUT,
                    "Session started at " + sessionStart + " exceeded the absolute timeout; all sessions revoked");
            log.info("[AUTH] Absolute session timeout: user={} sessionStart={} ip={}",
                    principal.getUsername(), sessionStart, ip);
            throw ExceptionFactory.unauthorized(AuthErrorCode.SESSION_ABSOLUTE_TIMEOUT);
        }

        String newAccessToken  = jwtTokenProvider.generateAccessToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken();
        String newTokenId      = UUID.randomUUID().toString();

        // Token rotation (B80): save new → mark old "used" → delete old. The "used" marker must
        // exist BEFORE the old key disappears, otherwise a concurrent replay landing in that gap
        // reads neither and gets diagnosed as an ordinary expiry instead of being detected.
        TokenStoreService.RotationResult rotation = tokenStore.rotateRefreshToken(
                principal.getUserId(), request.tokenId(), request.refreshToken(),
                newTokenId, newRefreshToken, sessionStart, principal.getAuthVersion());
        if (rotation != TokenStoreService.RotationResult.ROTATED) {
            if (tokenStore.wasRefreshTokenUsed(request.tokenId(), request.refreshToken(),
                    principal.getAuthVersion())) {
                revokeAllSessions(user);
                throw ExceptionFactory.unauthorized(AuthErrorCode.TOKEN_REUSE_DETECTED);
            }
            throw ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        // B81: carry the ORIGINAL session start over to the new tokenId. Stamping "now" here would
        // reset the absolute clock on every refresh and silently disable the timeout altogether —
        // with a byte-identical response, so nothing else would notice.
        // Concurrent refresh race: publish the result so a duplicate request racing on this exact
        // old tokenId (see the stored == null branch above) can be handed this pair instead of
        // being misdiagnosed as a reuse attack.

        // Extend device session TTL using the resolved deviceId
        String deviceId = resolveDeviceId(request.deviceId(), httpRequest, ip);
        tokenStore.extendDeviceSession(principal.getUserId(), deviceId);

        auditLogService.logAuth(principal.getUserId(), principal.getUsername(), ip, traceId,
                AuditAction.TOKEN_REFRESHED, "device=" + deviceId);

        return new AuthResponse(
                newAccessToken, newRefreshToken, newTokenId,
                jwtProperties.accessTokenExpiryMs() / 1000,
                deviceId,
                false);
    }

    // ── Logout ────────────────────────────────────────────────────────────

    public void logout(LogoutRequest request, HttpServletRequest httpRequest) {
        String ip      = (String) httpRequest.getAttribute("clientIp");
        String traceId = (String) httpRequest.getAttribute("traceId");
        String username = (String) httpRequest.getAttribute("authenticatedUserId");

        if (username == null) return;

        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(username);

        // Blacklist current access token
        String jti = (String) httpRequest.getAttribute("authenticatedJti");
        if (jti != null) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                long remaining = jwtTokenProvider.getRemainingTtlMs(authHeader.substring(7));
                tokenStore.blacklistAccessToken(jti, remaining);
            }
        }

        if (!tokenStore.matchesRefreshToken(principal.getUserId(), request.tokenId(),
                request.refreshToken(), principal.getAuthVersion())) {
            throw ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        tokenStore.deleteRefreshToken(principal.getUserId(), request.tokenId());

        auditLogService.logAuth(principal.getUserId(), principal.getUsername(), ip, traceId,
                AuditAction.LOGOUT, null);
        log.info("[AUTH] Logout: user={} ip={}", username, ip);
    }

    public void logoutAll(HttpServletRequest httpRequest) {
        String ip      = (String) httpRequest.getAttribute("clientIp");
        String traceId = (String) httpRequest.getAttribute("traceId");
        String username = (String) httpRequest.getAttribute("authenticatedUserId");

        if (username == null) return;

        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(username);

        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> ExceptionFactory.unauthorized(AuthErrorCode.INVALID_CREDENTIALS));
        revokeAllSessions(user);

        auditLogService.logAuth(principal.getUserId(), principal.getUsername(), ip, traceId,
                AuditAction.LOGOUT_ALL, "Logout from all devices");
        log.info("[AUTH] Logout-all: user={}", username);
    }

    // ── Account Recovery (D8c) ───────────────────────────────────────────

    /**
     * Account enumeration prevention (§4.13): the caller gets the exact same 200 response whether or
     * not {@code email} belongs to a real account, so this method deliberately returns {@code void}
     * and does nothing observable on a miss — the fixed message is composed by the controller, not by
     * branching on this method's outcome.
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String token = passwordResetTokenService.generateToken(user.getUserId());
            emailNotificationService.sendPasswordResetEmail(user.getEmail(), token);
            log.info("[AUTH] Password reset requested: user={}", user.getUsername());
        });
    }

    /**
     * Resolves the reset token, sets the new password, and — like a stolen-credential response —
     * force-logs-out every device: whoever asked for this reset no longer trusts whatever sessions
     * were live before it.
     */
    public void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        String ip      = (String) httpRequest.getAttribute("clientIp");
        String traceId = (String) httpRequest.getAttribute("traceId");

        UUID userId = passwordResetTokenService.consumeUserId(request.token())
                .orElseThrow(() -> ExceptionFactory.unauthorized(AuthErrorCode.RESET_TOKEN_INVALID));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.unauthorized(AuthErrorCode.RESET_TOKEN_INVALID));

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.revokeAllSessions();
        userRepository.save(user);

        tokenStore.deleteAllUserTokens(userId);
        tokenStore.deleteAllDeviceSessions(userId);

        auditLogService.logAuth(userId, user.getUsername(), ip, traceId,
                AuditAction.PASSWORD_RESET, "Password reset via forgot-password flow");
        log.info("[AUTH] Password reset completed: user={}", user.getUsername());
    }

    /** Admin-only manual unlock: clears the Redis fail-counter and reactivates the account. */
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = AuditAction.ACCOUNT_UNLOCKED, entityType = "User", entityIdExpression = "userId.toString()")
    public void adminUnlockAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ExceptionFactory.notFound(ValidationErrorCode.RESOURCE_NOT_FOUND, "User", userId));
        user.activate();
        userRepository.save(user);
        tokenStore.resetFailCount(user.getUsername());
        log.info("[AUTH] Account manually unlocked: user={}", user.getUsername());
    }

    // ── Me ────────────────────────────────────────────────────────────────

    /**
     * Self-service profile for the caller: {@code userId}/{@code email} the JWT cannot carry, and
     * permissions broken down by the company/plant they actually apply to.
     *
     * <p>{@code roles}/{@code permissions} are split straight from {@code principal.getAuthorities()}
     * — the same authorities {@link JwtTokenProvider#generateAccessToken} put in the token's
     * {@code roles} claim — so this always agrees with what the caller's own JWT already says.
     * {@code scopes}/{@code defaultPlantId} are the new part {@link AccessControlService} resolves.
     *
     * <p>Reachable only with a valid, unexpired token: {@code /api/auth/v1/me} is carved out of the
     * {@code permitAll} pattern in {@code SecurityConfig}, so {@code authenticatedUserId} is always
     * set by the time a request gets here — unlike {@link #refresh}, which is reached with an
     * <em>expired</em> token on purpose.
     */
    public MeResponse me(HttpServletRequest httpRequest) {
        String username = (String) httpRequest.getAttribute("authenticatedUserId");
        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(username);
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> ExceptionFactory.notFound(
                        ValidationErrorCode.RESOURCE_NOT_FOUND, "User", principal.getUserId()));

        Set<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .collect(Collectors.toSet());
        Set<String> permissions = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("PERM_"))
                .collect(Collectors.toSet());

        AccessControlService.UserAccessScopesResult scopes =
                accessControlService.resolveMyScopes(principal.getUserId());

        return new MeResponse(principal.getUserId(), user.getUsername(), user.getEmail(),
                user.getStatus().name(), roles, permissions, scopes.scopes(), scopes.defaultPlantId());
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Builds the response for a duplicate refresh request that lost the race to rotate a tokenId
     * someone else (presumably the same caller) already rotated moments ago — returns the SAME
     * already-issued pair rather than minting a second one. The access token is freshly minted, same
     * as every other call to {@code refresh}/{@code login}; it is not stored, so there is nothing to
     * reuse from the winner's response.
     */
    private AuthResponse respondWithExistingPair(UserPrincipal principal,
                                                 String tokenId,
                                                 String refreshToken,
                                                 RefreshRequest request,
                                                 HttpServletRequest httpRequest,
                                                 String ip) {
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String deviceId = resolveDeviceId(request.deviceId(), httpRequest, ip);
        tokenStore.extendDeviceSession(principal.getUserId(), deviceId);
        return new AuthResponse(
                accessToken, refreshToken, tokenId,
                jwtProperties.accessTokenExpiryMs() / 1000,
                deviceId,
                false);
    }

    /**
     * Brief, bounded wait paid only by a request that lost the {@code acquireRefreshLock} race —
     * gives the winner a moment to finish rotating and publish its result before this caller re-reads
     * the token store. Never blocks a normal, non-racing refresh call.
     */
    private void sleepBriefly() {
        try {
            Thread.sleep(CONCURRENT_REFRESH_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Resolves the effective device ID for this request.
     *
     * <ul>
     *   <li>If the client provides a non-blank {@code deviceId} (≤128 chars), use it directly.</li>
     *   <li>Otherwise, derive a stable ID server-side: {@code sha256(userAgent + ":" + ip)},
     *       truncated to 32 hex chars. This is deterministic per browser/IP pair, which is a
     *       reasonable fallback when the client cannot supply a stable ID.</li>
     * </ul>
     */
    private String resolveDeviceId(String clientDeviceId, HttpServletRequest httpRequest, String ip) {
        if (StringUtils.hasText(clientDeviceId)) {
            return clientDeviceId;
        }
        String userAgent = httpRequest.getHeader("User-Agent");
        String raw = (userAgent != null ? userAgent : "unknown") + ":" + ip;
        return "auto-" + sha256Prefix(raw, 24);
    }

    private String sha256Prefix(String input, int hexLength) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, Math.min(hexLength, sb.length()));
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, hexLength);
        }
    }

    private void incrementFailAndAudit(String username, String ip, String traceId) {
        tokenStore.incrementFailCount(username);
        auditLogService.logAuthFailure(username, ip, traceId,
                AuditAction.LOGIN_FAILED, "Invalid credentials for user: " + username);
    }

    private void revokeAllSessions(User user) {
        user.revokeAllSessions();
        userRepository.save(user);
        tokenStore.deleteAllUserTokens(user.getUserId());
        tokenStore.deleteAllDeviceSessions(user.getUserId());
    }
}
