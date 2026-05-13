package com.erp.manufacturing.module.auth.service;

import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.JwtProperties;
import com.erp.manufacturing.module.auth.dto.AuthResponse;
import com.erp.manufacturing.module.auth.dto.LoginRequest;
import com.erp.manufacturing.module.auth.dto.LogoutRequest;
import com.erp.manufacturing.module.auth.dto.RefreshRequest;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import com.erp.manufacturing.module.user.service.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Authentication service handling login, logout, and token refresh.
 *
 * <h3>Key behaviours:</h3>
 * <ul>
 *   <li><b>Brute-force protection</b> – max 5 failures in 15 min → temporary lockout</li>
 *   <li><b>Multi-device sessions</b> – each device (identified by {@code deviceId}) has an
 *       independent session. A user can be logged in from multiple devices simultaneously.
 *       If {@code deviceId} is not provided by the client, one is derived server-side
 *       from a hash of {@code User-Agent + IP}.</li>
 *   <li><b>Token rotation on refresh</b> – old tokenId deleted before issuing new pair</li>
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

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtTokenProvider       jwtTokenProvider;
    private final TokenStoreService      tokenStore;
    private final PasswordEncoder        passwordEncoder;
    private final AuditLogService        auditLogService;
    private final JwtProperties          jwtProperties;

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

        // Resolve deviceId: use client-provided value or derive from UA + IP
        String deviceId = resolveDeviceId(request.deviceId(), httpRequest, ip);

        // Register device session (replaces single-IP enforcement)
        tokenStore.saveDeviceSession(principal.getUserId(), deviceId, ip);

        // Issue token pair
        String accessToken  = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken();
        String tokenId      = UUID.randomUUID().toString();

        tokenStore.saveRefreshToken(principal.getUserId(), tokenId, refreshToken);

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
     * <p>The access token in the Authorization header may be expired here –
     * {@link com.erp.manufacturing.common.security.JwtAuthenticationFilter} sets
     * {@code authenticatedUserId} even for expired tokens on this path.
     * Security is enforced by the opaque refresh token + tokenId pair stored in Redis.
     */
    public AuthResponse refresh(RefreshRequest request, HttpServletRequest httpRequest) {
        String ip      = (String) httpRequest.getAttribute("clientIp");
        String traceId = (String) httpRequest.getAttribute("traceId");

        // userId comes from the JWT subject (set by filter even for expired tokens)
        String username = (String) httpRequest.getAttribute("authenticatedUserId");
        if (username == null) {
            throw ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(username);

        // Validate opaque refresh token
        String stored = tokenStore.getRefreshToken(principal.getUserId(), request.tokenId());
        if (stored == null || !stored.equals(request.refreshToken())) {
            throw ExceptionFactory.unauthorized(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // Token rotation: delete old, issue new
        tokenStore.deleteRefreshToken(principal.getUserId(), request.tokenId());

        String newAccessToken  = jwtTokenProvider.generateAccessToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken();
        String newTokenId      = UUID.randomUUID().toString();

        tokenStore.saveRefreshToken(principal.getUserId(), newTokenId, newRefreshToken);

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

        // Delete all refresh tokens and all device sessions
        tokenStore.deleteAllUserTokens(principal.getUserId());
        tokenStore.deleteAllDeviceSessions(principal.getUserId());

        auditLogService.logAuth(principal.getUserId(), principal.getUsername(), ip, traceId,
                AuditAction.LOGOUT_ALL, "Logout from all devices");
        log.info("[AUTH] Logout-all: user={}", username);
    }

    // ── Private helpers ───────────────────────────────────────────────────

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
}
