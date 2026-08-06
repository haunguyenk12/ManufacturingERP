package com.erp.manufacturing.module.auth.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.audit.AuditAction;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.module.auth.dto.ForgotPasswordRequest;
import com.erp.manufacturing.module.auth.dto.LoginRequest;
import com.erp.manufacturing.module.auth.dto.LogoutRequest;
import com.erp.manufacturing.module.auth.dto.MeResponse;
import com.erp.manufacturing.module.auth.dto.RefreshRequest;
import com.erp.manufacturing.module.auth.dto.ResetPasswordRequest;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.organization.dto.MyAccessScopeResponse;
import com.erp.manufacturing.module.organization.service.AccessControlService;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.repository.UserRepository;
import com.erp.manufacturing.module.user.service.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}.
 *
 * <p>Uses the current multi-device session API (saveDeviceSession / deleteAllDeviceSessions).
 * Error assertions check {@link AppException} with the correct {@link AuthErrorCode},
 * since the codebase no longer has dedicated exception subclasses per error type.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    /** Matches {@code app.jwt.absolute-session-timeout-ms} in application.yml (B81). */
    private static final long THIRTY_DAYS_MS = 2_592_000_000L;

    @Mock private UserDetailsServiceImpl          userDetailsService;
    @Mock private JwtTokenProvider                jwtTokenProvider;
    @Mock private TokenStoreService               tokenStore;
    @Mock private PasswordEncoder                 passwordEncoder;
    @Mock private AuditLogService                 auditLogService;
    @Mock private com.erp.manufacturing.config.JwtProperties jwtProperties;
    @Mock private AccessControlService            accessControlService;
    @Mock private UserRepository                  userRepository;
    @Mock private PasswordResetTokenService       passwordResetTokenService;
    @Mock private EmailNotificationService        emailNotificationService;

    @InjectMocks
    private AuthService authService;

    @Mock
    private HttpServletRequest httpRequest;

    private User          testUser;
    private UserPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        Role adminRole = Role.builder()
                .roleId(UUID.randomUUID())
                .name("ADMIN")
                .build();
        testUser = User.builder()
                .userId(UUID.randomUUID())
                .username("testuser")
                .email("test@erp.local")
                .password("$2a$12$encodedPassword")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(adminRole))
                .build();
        testPrincipal = new UserPrincipal(testUser);

        // lenient: the /me tests below never touch AuditLogService, so they never read these two
        // attributes — unlike every other test in this class, which does via an audit call.
        lenient().when(httpRequest.getAttribute("clientIp")).thenReturn("192.168.1.100");
        lenient().when(httpRequest.getAttribute("traceId")).thenReturn("abc123");

        // lenient: only the tests under "Concurrent refresh race" below care about the lock outcome.
        // Defaulting every other refresh_* test to "lock acquired" keeps them on the fast path —
        // an unstubbed boolean-returning mock method returns false, which would otherwise make every
        // single refresh test in this class pay the real 150ms sleepBriefly() wait for no reason.
        lenient().when(tokenStore.acquireRefreshLock(anyString())).thenReturn(true);
    }

    // ── Login success ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Login success – issues access + refresh token")
    void login_success() {
        when(tokenStore.getFailCount("testuser")).thenReturn(0L);
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(passwordEncoder.matches("password123", testPrincipal.getPassword())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(testPrincipal)).thenReturn("access.token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        var response = authService.login(new LoginRequest("testuser", "password123", null), httpRequest);

        assertThat(response.accessToken()).isEqualTo("access.token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(900L);

        // Device session registered
        verify(tokenStore).saveDeviceSession(eq(testUser.getUserId()), any(), eq("192.168.1.100"));
        // Refresh token persisted
        verify(tokenStore).saveRefreshToken(eq(testUser.getUserId()), any(), eq("refresh-token"));
        // Fail counter cleared
        verify(tokenStore).resetFailCount("testuser");
    }

    @Test
    @DisplayName("Login success – client-provided deviceId is used")
    void login_success_withClientDeviceId() {
        when(tokenStore.getFailCount("testuser")).thenReturn(0L);
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(testPrincipal)).thenReturn("access.token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        var response = authService.login(new LoginRequest("testuser", "password123", "my-device-001"), httpRequest);

        assertThat(response.deviceId()).isEqualTo("my-device-001");
        verify(tokenStore).saveDeviceSession(eq(testUser.getUserId()), eq("my-device-001"), eq("192.168.1.100"));
    }

    // ── Login failure – wrong password ─────────────────────────────────────

    @Test
    @DisplayName("Login fails on invalid credentials – throws AppException AUTH_001")
    void login_failsOnInvalidCredentials() {
        when(tokenStore.getFailCount("testuser")).thenReturn(0L);
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("testuser", "wrongpass", null), httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException ae = (AppException) ex;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
                });

        verify(tokenStore).incrementFailCount("testuser");
    }

    @Test
    @DisplayName("Login fails when user not found – same exception (enumeration prevention)")
    void login_failsOnUserNotFound() {
        when(tokenStore.getFailCount("ghost")).thenReturn(0L);
        when(userDetailsService.loadUserByUsername("ghost"))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("not found"));

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("ghost", "anypass", null), httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException ae = (AppException) ex;
                    // Must return the SAME error code as wrong password – no enumeration
                    assertThat(ae.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
                });
    }

    // ── Login failure – account locked ────────────────────────────────────

    @Test
    @DisplayName("Login blocked after max failed attempts – throws AppException AUTH_002")
    void login_blockedAfterMaxFailedAttempts() {
        when(tokenStore.getFailCount("testuser")).thenReturn(5L);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("testuser", "any", null), httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException ae = (AppException) ex;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);
                });

        // User lookup must NOT happen – brute-force check short-circuits
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    // ── Login failure – inactive account ──────────────────────────────────

    @Test
    @DisplayName("Login fails for inactive account – throws AppException AUTH_003")
    void login_failsForInactiveAccount() {
        User inactiveUser = User.builder()
                .userId(UUID.randomUUID())
                .username("inactive")
                .email("inactive@erp.local")
                .password("$2a$12$hash")
                .status(UserStatus.INACTIVE)
                .roles(Set.of())
                .build();
        UserPrincipal inactivePrincipal = new UserPrincipal(inactiveUser);

        when(tokenStore.getFailCount("inactive")).thenReturn(0L);
        when(userDetailsService.loadUserByUsername("inactive")).thenReturn(inactivePrincipal);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("inactive", "pass", null), httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException ae = (AppException) ex;
                    assertThat(ae.getErrorCode()).isEqualTo(AuthErrorCode.ACCOUNT_INACTIVE);
                });
    }

    // ── Refresh ────────────────────────────────────────────────────────────
    //
    // Note: the request attribute is named "authenticatedUserId" but actually carries the
    // JWT *subject*, i.e. the username – the service feeds it into loadUserByUsername().

    @Test
    @DisplayName("Refresh success – rotates the token pair and extends the device session")
    void refresh_validToken_rotatesAndReturnsNewPair() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn("old-refresh");
        when(jwtTokenProvider.generateAccessToken(testPrincipal)).thenReturn("new.access");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        var response = authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest);

        assertThat(response.accessToken()).isEqualTo("new.access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.tokenId()).isNotEqualTo("old-tid");
        assertThat(response.expiresIn()).isEqualTo(900L);

        // Rotation must both revoke the old tokenId AND persist the new one
        verify(tokenStore).deleteRefreshToken(testUser.getUserId(), "old-tid");
        verify(tokenStore).saveRefreshToken(eq(testUser.getUserId()), eq(response.tokenId()), eq("new-refresh"));
        verify(tokenStore).extendDeviceSession(testUser.getUserId(), response.deviceId());

        // B80: the "used" marker must be written while the old key still exists. Any other order
        // leaves a gap where a concurrent replay reads neither and looks like a plain expiry.
        InOrder rotation = inOrder(tokenStore);
        rotation.verify(tokenStore).saveRefreshToken(eq(testUser.getUserId()), eq(response.tokenId()), eq("new-refresh"));
        rotation.verify(tokenStore).markRefreshTokenUsed("old-tid");
        rotation.verify(tokenStore).deleteRefreshToken(testUser.getUserId(), "old-tid");

        // Concurrent refresh race: the winner publishes the result so a duplicate request racing on
        // "old-tid" can be handed this same pair instead of being misdiagnosed as a reuse attack.
        verify(tokenStore).saveRotationResult("old-tid", response.tokenId());
    }

    @Test
    @DisplayName("Refresh with a rotated-away tokenId – TOKEN_REUSE_DETECTED, every session revoked")
    void refresh_reusedToken_throwsTokenReuseDetectedAndForceLogoutAll() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn(null);
        when(tokenStore.wasRefreshTokenUsed("old-tid")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.TOKEN_REUSE_DETECTED));

        // Force logout covers BOTH refresh tokens and device sessions, same as logoutAll()
        verify(tokenStore).deleteAllUserTokens(testUser.getUserId());
        verify(tokenStore).deleteAllDeviceSessions(testUser.getUserId());
        verify(auditLogService).logAuthFailure(eq("testuser"), eq("192.168.1.100"), eq("abc123"),
                eq(AuditAction.SUSPICIOUS_TOKEN_REUSE), anyString());

        // No new pair may leak out of a request we just classified as an attack
        verify(tokenStore, never()).saveRefreshToken(any(), any(), any());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("Refresh without authenticated subject – REFRESH_TOKEN_EXPIRED, token store untouched")
    void refresh_missingUserIdAttribute_throwsRefreshTokenExpired() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn(null);

        assertThatThrownBy(() ->
                authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.REFRESH_TOKEN_EXPIRED));

        verifyNoInteractions(tokenStore, userDetailsService);
    }

    @Test
    @DisplayName("Refresh with unknown tokenId – REFRESH_TOKEN_EXPIRED, nothing is rotated")
    void refresh_storedTokenNull_throwsRefreshTokenExpired() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn(null);
        // Never rotated away within the RTR window ⇒ this is an ordinary expiry, not a replay (B80)
        when(tokenStore.wasRefreshTokenUsed("old-tid")).thenReturn(false);

        assertThatThrownBy(() ->
                authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.REFRESH_TOKEN_EXPIRED));

        verify(tokenStore, never()).deleteRefreshToken(any(), any());
        verify(tokenStore, never()).saveRefreshToken(any(), any(), any());
    }

    @Test
    @DisplayName("Refresh with mismatched refresh token – REFRESH_TOKEN_EXPIRED, no new token issued")
    void refresh_storedTokenMismatch_throwsRefreshTokenExpired() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn("other-refresh");

        assertThatThrownBy(() ->
                authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.REFRESH_TOKEN_EXPIRED));

        verify(tokenStore, never()).deleteRefreshToken(any(), any());
        verify(tokenStore, never()).saveRefreshToken(any(), any(), any());
        verifyNoInteractions(jwtTokenProvider);
        // B80: RTR covers only the "tokenId not found" branch. Here the tokenId still exists, so
        // it was never rotated away — asking about reuse would be answering the wrong question.
        verify(tokenStore, never()).wasRefreshTokenUsed(any());
        verify(tokenStore, never()).deleteAllUserTokens(any());
    }

    // ── Concurrent refresh race ─────────────────────────────────────────────
    // Fixes the false positive documented in common/security/CLAUDE.md §4.12: a client retry that
    // still holds the OLD (tokenId, refreshToken) — because it never received the winner's response —
    // must be handed the already-rotated pair, not force-logged-out as a thief.

    @Test
    @DisplayName("Refresh racing a just-completed rotation of the same tokenId absorbs the winner's " +
            "pair instead of throwing TOKEN_REUSE_DETECTED")
    void refresh_concurrentDuplicate_absorbsRotationResultInsteadOfThrowingReuseDetected() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        // "old-tid" was already rotated away by a winner request moments ago.
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn(null);
        when(tokenStore.getRotationResult("old-tid")).thenReturn("new-tid");
        when(tokenStore.getRefreshToken(testUser.getUserId(), "new-tid")).thenReturn("winners-refresh");
        when(jwtTokenProvider.generateAccessToken(testPrincipal)).thenReturn("fresh.access");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        var response = authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest);

        assertThat(response.accessToken()).isEqualTo("fresh.access");
        assertThat(response.refreshToken()).isEqualTo("winners-refresh");
        assertThat(response.tokenId()).isEqualTo("new-tid");

        // This must NOT be diagnosed as an attack: no reuse check, no force-logout, no second pair
        // minted, no re-rotation of anything.
        verify(tokenStore, never()).wasRefreshTokenUsed(any());
        verify(tokenStore, never()).deleteAllUserTokens(any());
        verify(tokenStore, never()).deleteAllDeviceSessions(any());
        verify(tokenStore, never()).saveRefreshToken(any(), any(), any());
        verify(tokenStore, never()).saveSessionStart(any(), any(), any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Refresh racing a just-completed rotation still extends the resolved device session")
    void refresh_concurrentDuplicate_extendsDeviceSession() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn(null);
        when(tokenStore.getRotationResult("old-tid")).thenReturn("new-tid");
        when(tokenStore.getRefreshToken(testUser.getUserId(), "new-tid")).thenReturn("winners-refresh");
        when(jwtTokenProvider.generateAccessToken(testPrincipal)).thenReturn("fresh.access");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        var response = authService.refresh(
                new RefreshRequest("old-refresh", "old-tid", "device-42"), httpRequest);

        assertThat(response.deviceId()).isEqualTo("device-42");
        verify(tokenStore).extendDeviceSession(testUser.getUserId(), "device-42");
    }

    @Test
    @DisplayName("A rotation-result breadcrumb whose target pair no longer exists is ignored " +
            "(falls through to the normal reuse check instead of failing)")
    void refresh_rotationResultTargetGone_fallsThroughToReuseCheck() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn(null);
        when(tokenStore.getRotationResult("old-tid")).thenReturn("new-tid");
        // The referenced new pair is gone too (e.g. it also expired) — don't hand out nothing.
        when(tokenStore.getRefreshToken(testUser.getUserId(), "new-tid")).thenReturn(null);
        when(tokenStore.wasRefreshTokenUsed("old-tid")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.TOKEN_REUSE_DETECTED));
    }

    @Test
    @DisplayName("Losing the advisory lock does not skip real reuse detection when there is no " +
            "rotation breadcrumb to absorb")
    void refresh_lockNotAcquired_stillDetectsGenuineReuseWhenNoBreadcrumbExists() {
        when(tokenStore.acquireRefreshLock("old-tid")).thenReturn(false);
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn(null);
        when(tokenStore.getRotationResult("old-tid")).thenReturn(null);
        when(tokenStore.wasRefreshTokenUsed("old-tid")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.TOKEN_REUSE_DETECTED));

        verify(tokenStore).deleteAllUserTokens(testUser.getUserId());
    }

    // ── Absolute session timeout (B81, D8b) ────────────────────────────────

    @Test
    @DisplayName("Refresh of a session past the absolute timeout – SESSION_ABSOLUTE_TIMEOUT, every session revoked")
    void refresh_sessionOlderThanAbsoluteTimeout_throwsAndForceLogoutAll() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn("old-refresh");
        when(tokenStore.getSessionStart(testUser.getUserId(), "old-tid"))
                .thenReturn(Instant.now().minus(31, ChronoUnit.DAYS));
        when(jwtProperties.absoluteSessionTimeoutMs()).thenReturn(THIRTY_DAYS_MS);

        assertThatThrownBy(() ->
                authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.SESSION_ABSOLUTE_TIMEOUT));

        verify(tokenStore).deleteAllUserTokens(testUser.getUserId());
        verify(tokenStore).deleteAllDeviceSessions(testUser.getUserId());
        verify(auditLogService).logAuthFailure(eq("testuser"), eq("192.168.1.100"), eq("abc123"),
                eq(AuditAction.SESSION_ABSOLUTE_TIMEOUT), anyString());

        // B81: the check must sit BEFORE rotation — a retired session may not walk away with a
        // fresh pair. Same error code and same 401 either way, so only these verifies catch it.
        verify(tokenStore, never()).saveRefreshToken(any(), any(), any());
        verify(tokenStore, never()).saveSessionStart(any(), any(), any());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("Refresh just under the absolute timeout – still rotates normally")
    void refresh_sessionJustUnderAbsoluteTimeout_rotatesNormally() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn("old-refresh");
        when(tokenStore.getSessionStart(testUser.getUserId(), "old-tid"))
                .thenReturn(Instant.now().minus(29, ChronoUnit.DAYS));
        when(jwtProperties.absoluteSessionTimeoutMs()).thenReturn(THIRTY_DAYS_MS);
        when(jwtTokenProvider.generateAccessToken(testPrincipal)).thenReturn("new.access");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        var response = authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest);

        assertThat(response.accessToken()).isEqualTo("new.access");
        verify(tokenStore, never()).deleteAllUserTokens(any());
    }

    @Test
    @DisplayName("Rotation carries the ORIGINAL session start forward – it does not restart the absolute clock")
    void refresh_carriesTheOriginalSessionStartForwardToTheNewTokenId() {
        Instant originalStart = Instant.now().minus(20, ChronoUnit.DAYS);
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn("old-refresh");
        when(tokenStore.getSessionStart(testUser.getUserId(), "old-tid")).thenReturn(originalStart);
        when(jwtProperties.absoluteSessionTimeoutMs()).thenReturn(THIRTY_DAYS_MS);
        when(jwtTokenProvider.generateAccessToken(testPrincipal)).thenReturn("new.access");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        var response = authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest);

        // B81: the EXACT original instant, not "now". Stamping now here would reset the absolute
        // clock on every refresh and disable the timeout — with a byte-identical response, so this
        // assertion on the argument is the only thing standing between the feature and a no-op.
        verify(tokenStore).saveSessionStart(testUser.getUserId(), response.tokenId(), originalStart);
    }

    @Test
    @DisplayName("Refresh of a pre-D8b session (no start stamp) – treated as starting now, not as expired")
    void refresh_sessionWithoutStartStamp_isTreatedAsStartingNow() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(tokenStore.getRefreshToken(testUser.getUserId(), "old-tid")).thenReturn("old-refresh");
        when(tokenStore.getSessionStart(testUser.getUserId(), "old-tid")).thenReturn(null);
        when(jwtTokenProvider.generateAccessToken(testPrincipal)).thenReturn("new.access");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        Instant beforeCall = Instant.now();
        var response = authService.refresh(new RefreshRequest("old-refresh", "old-tid", null), httpRequest);

        // Fail-open on purpose: sessions established before D8b have no stamp to judge. Failing
        // closed would log out every signed-in user the moment this deploys, buying no security.
        verify(tokenStore, never()).deleteAllUserTokens(any());
        ArgumentCaptor<Instant> stamped = ArgumentCaptor.forClass(Instant.class);
        verify(tokenStore).saveSessionStart(eq(testUser.getUserId()), eq(response.tokenId()), stamped.capture());
        assertThat(stamped.getValue()).isBetween(beforeCall, Instant.now());
    }

    @Test
    @DisplayName("Login – stamps the session start the absolute timeout is later measured from")
    void login_recordsTheSessionStart() {
        when(tokenStore.getFailCount("testuser")).thenReturn(0L);
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(passwordEncoder.matches("password123", testPrincipal.getPassword())).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(testPrincipal)).thenReturn("access.token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900_000L);

        Instant beforeCall = Instant.now();
        var response = authService.login(new LoginRequest("testuser", "password123", null), httpRequest);

        ArgumentCaptor<Instant> stamped = ArgumentCaptor.forClass(Instant.class);
        verify(tokenStore).saveSessionStart(eq(testUser.getUserId()), eq(response.tokenId()), stamped.capture());
        assertThat(stamped.getValue()).isBetween(beforeCall, Instant.now());
    }

    // ── Logout ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Logout – blacklists the current access token and deletes its refresh token")
    void logout_withJtiAndBearer_blacklistsAndDeletesRefresh() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(httpRequest.getAttribute("authenticatedJti")).thenReturn("jti-1");
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer abc");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(jwtTokenProvider.getRemainingTtlMs("abc")).thenReturn(120_000L);

        authService.logout(new LogoutRequest("refresh-token", "tid"), httpRequest);

        verify(tokenStore).blacklistAccessToken("jti-1", 120_000L);
        verify(tokenStore).deleteRefreshToken(testUser.getUserId(), "tid");
    }

    @Test
    @DisplayName("Logout without authenticated subject – returns silently, touches nothing")
    void logout_nullUserId_returnsSilently() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn(null);

        authService.logout(new LogoutRequest("refresh-token", "tid"), httpRequest);

        verifyNoInteractions(tokenStore, userDetailsService, jwtTokenProvider, auditLogService);
    }

    @Test
    @DisplayName("Logout without jti – skips blacklisting but still deletes the refresh token")
    void logout_nullJti_skipsBlacklist_stillDeletesRefresh() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(httpRequest.getAttribute("authenticatedJti")).thenReturn(null);
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);

        authService.logout(new LogoutRequest("refresh-token", "tid"), httpRequest);

        verify(tokenStore, never()).blacklistAccessToken(any(), anyLong());
        verify(tokenStore).deleteRefreshToken(testUser.getUserId(), "tid");
    }

    // ── Logout all devices ─────────────────────────────────────────────────

    @Test
    @DisplayName("Logout-all – drops every refresh token and every device session")
    void logoutAll_deletesAllTokensAndSessions() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);

        authService.logoutAll(httpRequest);

        verify(tokenStore).deleteAllUserTokens(testUser.getUserId());
        verify(tokenStore).deleteAllDeviceSessions(testUser.getUserId());
    }

    @Test
    @DisplayName("Logout-all without authenticated subject – returns silently, touches nothing")
    void logoutAll_nullUserId_returnsSilently() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn(null);

        authService.logoutAll(httpRequest);

        verifyNoInteractions(tokenStore, userDetailsService, auditLogService);
    }

    // ── Account Recovery (D8c) ──────────────────────────────────────────────

    @Test
    @DisplayName("forgotPassword – existing email generates a token and sends the mock email")
    void forgotPassword_existingEmail_generatesTokenAndSendsEmail() {
        when(userRepository.findByEmail("test@erp.local")).thenReturn(Optional.of(testUser));
        when(passwordResetTokenService.generateToken(testUser.getUserId())).thenReturn("reset-tok-1");

        authService.forgotPassword(new ForgotPasswordRequest("test@erp.local"));

        verify(passwordResetTokenService).generateToken(testUser.getUserId());
        verify(emailNotificationService).sendPasswordResetEmail("test@erp.local", "reset-tok-1");
    }

    /**
     * Account enumeration prevention (§4.13): a miss must be byte-identical in behaviour to a hit as
     * far as any collaborator is concerned — nothing is generated, nothing is sent.
     */
    @Test
    @DisplayName("forgotPassword – unknown email touches neither the token service nor the mailer")
    void forgotPassword_unknownEmail_doesNothingObservable() {
        when(userRepository.findByEmail("nobody@erp.local")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest("nobody@erp.local"));

        verifyNoInteractions(passwordResetTokenService, emailNotificationService);
    }

    @Test
    @DisplayName("resetPassword – valid token updates the password and force-logs-out every session")
    void resetPassword_validToken_updatesPasswordAndForcesLogoutEverywhere() {
        when(passwordResetTokenService.resolveUserId("reset-tok-1"))
                .thenReturn(Optional.of(testUser.getUserId()));
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("$2a$12$newEncodedPassword");

        authService.resetPassword(
                new ResetPasswordRequest("reset-tok-1", "NewPassw0rd!"), httpRequest);

        assertThat(testUser.getPassword()).isEqualTo("$2a$12$newEncodedPassword");
        verify(userRepository).save(testUser);
        verify(passwordResetTokenService).invalidate("reset-tok-1", testUser.getUserId());
        verify(tokenStore).deleteAllUserTokens(testUser.getUserId());
        verify(tokenStore).deleteAllDeviceSessions(testUser.getUserId());
        verify(auditLogService).logAuth(eq(testUser.getUserId()), eq("testuser"),
                anyString(), anyString(), eq(AuditAction.PASSWORD_RESET), anyString());
    }

    @Test
    @DisplayName("resetPassword – unknown/expired token throws RESET_TOKEN_INVALID before touching anything")
    void resetPassword_invalidToken_throwsResetTokenInvalidBeforeTouchingAnything() {
        when(passwordResetTokenService.resolveUserId("bad-tok")).thenReturn(Optional.empty());

        ResetPasswordRequest request = new ResetPasswordRequest("bad-tok", "NewPassw0rd!");
        assertThatThrownBy(() -> authService.resetPassword(request, httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.RESET_TOKEN_INVALID));

        verifyNoInteractions(userRepository, tokenStore, auditLogService);
    }

    @Test
    @DisplayName("adminUnlockAccount – reactivates the account and clears the Redis fail-counter")
    void adminUnlockAccount_resetsStatusAndFailCount() {
        testUser.setStatus(UserStatus.LOCKED);
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));

        authService.adminUnlockAccount(testUser.getUserId());

        assertThat(testUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(testUser);
        verify(tokenStore).resetFailCount("testuser");
    }

    // ── Me ────────────────────────────────────────────────────────────────
    //
    // /me is only reachable with a valid, unexpired token (SecurityConfig carves it out of
    // permitAll), so unlike refresh()/logout() there is no "missing authenticatedUserId" branch
    // to test here — the filter chain never lets that request reach the controller.

    @Test
    @DisplayName("Me – returns profile with userId/email from User, and roles/permissions split from authorities")
    void me_returnsProfileWithRolesPermissionsScopes() {
        UserPrincipal principalWithPermissions = new UserPrincipal(
                testUser, Set.of(), Set.of("PERM_WORK_ORDER_MANAGE", "PERM_MRP_RUN"));

        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(principalWithPermissions);
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));

        MyAccessScopeResponse plantScope = new MyAccessScopeResponse(
                "PLANT", UUID.randomUUID(), "CO-01", UUID.randomUUID(), "PL-HN",
                Set.of("PERM_WORK_ORDER_MANAGE"));
        UUID defaultPlantId = plantScope.plantId();
        when(accessControlService.resolveMyScopes(testUser.getUserId()))
                .thenReturn(new AccessControlService.UserAccessScopesResult(List.of(plantScope), defaultPlantId));

        MeResponse response = authService.me(httpRequest);

        assertThat(response.userId()).isEqualTo(testUser.getUserId());
        assertThat(response.username()).isEqualTo("testuser");
        assertThat(response.email()).isEqualTo("test@erp.local");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.roles()).containsExactly("ADMIN");
        assertThat(response.permissions()).containsExactlyInAnyOrder("PERM_WORK_ORDER_MANAGE", "PERM_MRP_RUN");
        assertThat(response.scopes()).containsExactly(plantScope);
        assertThat(response.defaultPlantId()).isEqualTo(defaultPlantId);
    }

    @Test
    @DisplayName("Me – strips ROLE_/PERM_ prefixes when splitting authorities")
    void me_splitsAuthorities_stripsRoleAndPermPrefixes() {
        UserPrincipal principalWithPermissions = new UserPrincipal(
                testUser, Set.of(), Set.of("PERM_QUALITY_DISPOSITION"));

        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(principalWithPermissions);
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));
        when(accessControlService.resolveMyScopes(testUser.getUserId()))
                .thenReturn(new AccessControlService.UserAccessScopesResult(List.of(), null));

        MeResponse response = authService.me(httpRequest);

        assertThat(response.roles()).allSatisfy(role -> assertThat(role).doesNotStartWith("ROLE_"));
        assertThat(response.permissions()).allSatisfy(perm -> assertThat(perm).startsWith("PERM_"));
        assertThat(response.roles()).containsExactly("ADMIN");
        assertThat(response.permissions()).containsExactly("PERM_QUALITY_DISPOSITION");
    }

    @Test
    @DisplayName("Me – user missing from repository throws AppException RESOURCE_NOT_FOUND")
    void me_userNotFoundInRepository_throwsResourceNotFound() {
        when(httpRequest.getAttribute("authenticatedUserId")).thenReturn("testuser");
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me(httpRequest))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(com.erp.manufacturing.common.exception.ValidationErrorCode.RESOURCE_NOT_FOUND));

        verifyNoInteractions(accessControlService);
    }
}
