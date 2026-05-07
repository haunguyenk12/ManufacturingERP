package com.erp.manufacturing.module.auth.service;

import com.erp.manufacturing.common.exception.InvalidCredentialsException;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.module.auth.dto.LoginRequest;
import com.erp.manufacturing.module.user.domain.Role;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserDetailsServiceImpl userDetailsService;
    @Mock private JwtTokenProvider       jwtTokenProvider;
    @Mock private TokenStoreService      tokenStore;
    @Mock private PasswordEncoder        passwordEncoder;
    @Mock private AuditLogService        auditLogService;
    @Mock private com.erp.manufacturing.config.JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    @Mock
    private HttpServletRequest httpRequest;

    private User testUser;
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

    @Test
    @DisplayName("Login success – no previous session")
    void login_success_noExistingSession() {
        when(tokenStore.getFailCount("testuser")).thenReturn(0L);
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(passwordEncoder.matches("password123", testPrincipal.getPassword())).thenReturn(true);
        when(tokenStore.getSessionIp(testUser.getUserId())).thenReturn(null);
        when(jwtTokenProvider.generateAccessToken(testPrincipal)).thenReturn("access.token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900000L);

        var response = authService.login(new LoginRequest("testuser", "password123"), httpRequest);

        assertThat(response.accessToken()).isEqualTo("access.token");
        assertThat(response.sessionKicked()).isFalse();
        verify(tokenStore).saveRefreshToken(eq(testUser.getUserId()), any(), eq("refresh-token"));
        verify(tokenStore).saveSessionIp(testUser.getUserId(), "192.168.1.100");
        verify(tokenStore).resetFailCount("testuser");
    }

    @Test
    @DisplayName("Login kicks previous session when IP differs")
    void login_kicksExistingSession_whenIpDiffers() {
        when(tokenStore.getFailCount("testuser")).thenReturn(0L);
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(tokenStore.getSessionIp(testUser.getUserId())).thenReturn("10.0.0.1"); // old IP
        when(jwtTokenProvider.generateAccessToken(testPrincipal)).thenReturn("token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh");
        when(jwtProperties.accessTokenExpiryMs()).thenReturn(900000L);

        var response = authService.login(new LoginRequest("testuser", "password123"), httpRequest);

        assertThat(response.sessionKicked()).isTrue();
        verify(tokenStore).deleteAllUserTokens(testUser.getUserId());
    }

    @Test
    @DisplayName("Login fails – invalid credentials")
    void login_failsOnInvalidCredentials() {
        when(tokenStore.getFailCount("testuser")).thenReturn(0L);
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(testPrincipal);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("testuser", "wrongpass"), httpRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(tokenStore).incrementFailCount("testuser");
    }

    @Test
    @DisplayName("Login blocked after max failed attempts")
    void login_blockedAfterMaxFailedAttempts() {
        when(tokenStore.getFailCount("testuser")).thenReturn(5L);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("testuser", "any"), httpRequest))
                .isInstanceOf(com.erp.manufacturing.common.exception.AccountLockedException.class);

        verify(userDetailsService, never()).loadUserByUsername(any());
    }
}
