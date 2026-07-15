package com.erp.manufacturing.module.auth.service;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.module.auth.dto.LoginRequest;
import com.erp.manufacturing.module.organization.domain.Role;
import com.erp.manufacturing.module.user.domain.User;
import com.erp.manufacturing.module.user.domain.UserPrincipal;
import com.erp.manufacturing.module.user.domain.UserStatus;
import com.erp.manufacturing.module.user.service.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    @Mock private UserDetailsServiceImpl          userDetailsService;
    @Mock private JwtTokenProvider                jwtTokenProvider;
    @Mock private TokenStoreService               tokenStore;
    @Mock private PasswordEncoder                 passwordEncoder;
    @Mock private AuditLogService                 auditLogService;
    @Mock private com.erp.manufacturing.config.JwtProperties jwtProperties;

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

        when(httpRequest.getAttribute("clientIp")).thenReturn("192.168.1.100");
        when(httpRequest.getAttribute("traceId")).thenReturn("abc123");
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
}
