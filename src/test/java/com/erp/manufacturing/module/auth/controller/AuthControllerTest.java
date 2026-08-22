package com.erp.manufacturing.module.auth.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.auth.dto.AuthResponse;
import com.erp.manufacturing.module.auth.dto.ForgotPasswordRequest;
import com.erp.manufacturing.module.auth.dto.LoginRequest;
import com.erp.manufacturing.module.auth.dto.MeResponse;
import com.erp.manufacturing.module.auth.dto.RefreshRequest;
import com.erp.manufacturing.module.auth.dto.ResetPasswordRequest;
import com.erp.manufacturing.module.auth.service.AuthService;
import com.erp.manufacturing.module.organization.dto.MyAccessScopeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link AuthController} – locks the {@code {code,result,message}} envelope
 * for the permitAll auth endpoints (`.claude/rules/error-handling.md` §5.1).
 *
 * <p>{@code AuthService} is mocked; {@code @PreAuthorize}/permission checks don't apply here
 * (AuthController is permitAll) — that's already covered by service-level method-security tests.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController – response envelope contract")
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthService authService;

    // Unused directly by these tests – required only so the auto-detected security filters
    // (JwtAuthenticationFilter, RateLimitFilter, UserRateLimitFilter, TraceIdFilter) can be
    // constructed by the @WebMvcTest slice, even with addFilters = false.
    @MockBean
    IpExtractor ipExtractor;
    @MockBean
    JwtTokenProvider jwtTokenProvider;
    @MockBean
    TokenStoreService tokenStoreService;
    @MockBean
    UserDetailsService userDetailsService;
    @MockBean
    RedisTemplate<String, String> redisTemplate;
    @MockBean
    RateLimitProperties rateLimitProperties;

    @Test
    @DisplayName("login: valid credentials returns 200 with AuthResponse payload")
    void login_validCredentials_returns200WithAuthResponse() throws Exception {
        AuthResponse response = new AuthResponse("access.jwt", "refresh-uuid", "tid-1", 900L, "device-1", false);
        when(authService.login(any(LoginRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret123","deviceId":"device-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.accessToken").value("access.jwt"))
                .andExpect(jsonPath("$.result.tokenId").value("tid-1"));
    }

    @Test
    @DisplayName("login: blank username fails @Valid before reaching the service")
    void login_blankUsername_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"secret123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("login: invalid credentials returns 401 INVALID_CREDENTIALS")
    void login_invalidCredentials_returns401InvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new AppException(AuthErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_CREDENTIALS.code()));
    }

    @Test
    @DisplayName("login: account locked returns 423 ACCOUNT_LOCKED")
    void login_accountLocked_returns423AccountLocked() throws Exception {
        when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new AppException(AuthErrorCode.ACCOUNT_LOCKED));

        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret123"}
                                """))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.ACCOUNT_LOCKED.code()));
    }

    @Test
    @WithMockUser
    @DisplayName("logout: valid request returns 200 with null result (noContent envelope)")
    void logout_validRequest_returns200NoContent() throws Exception {
        mockMvc.perform(post("/auth/v1/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh-uuid","tokenId":"tid-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("refresh: expired/invalid refresh token returns 401 REFRESH_TOKEN_EXPIRED")
    void refresh_expiredToken_returns401RefreshTokenExpired() throws Exception {
        when(authService.refresh(any(RefreshRequest.class), any()))
                .thenThrow(new AppException(AuthErrorCode.REFRESH_TOKEN_EXPIRED));

        mockMvc.perform(post("/auth/v1/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"old-refresh","tokenId":"old-tid"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.REFRESH_TOKEN_EXPIRED.code()));
    }

    @Test
    @DisplayName("refresh: replayed refresh token returns 401 TOKEN_REUSE_DETECTED")
    void refresh_reusedToken_returns401TokenReuseDetected() throws Exception {
        when(authService.refresh(any(RefreshRequest.class), any()))
                .thenThrow(new AppException(AuthErrorCode.TOKEN_REUSE_DETECTED));

        mockMvc.perform(post("/auth/v1/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"old-refresh","tokenId":"old-tid"}
                                """))
                .andExpect(status().isUnauthorized())
                // Same 401 as REFRESH_TOKEN_EXPIRED — the code is the only thing that distinguishes
                // "please log in again" from "your session was revoked because of a replay".
                .andExpect(jsonPath("$.code").value(AuthErrorCode.TOKEN_REUSE_DETECTED.code()));
    }

    @Test
    @DisplayName("logout-all: returns 200 with SUCCESS envelope")
    void logoutAll_returns200() throws Exception {
        mockMvc.perform(post("/auth/v1/logout-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @WithMockUser
    @DisplayName("me: returns 200 with profile, permissions, and scopes envelope")
    void me_returns200WithEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID plantId = UUID.randomUUID();
        MyAccessScopeResponse scope = new MyAccessScopeResponse(
                "PLANT", UUID.randomUUID(), "CO-01", plantId, "PL-HN", Set.of("PERM_WORK_ORDER_MANAGE"));
        MeResponse response = new MeResponse(userId, "admin", "admin@erp.local", "ACTIVE",
                Set.of("ADMIN"), Set.of("PERM_WORK_ORDER_MANAGE"), List.of(scope), plantId);
        when(authService.me(any())).thenReturn(response);

        mockMvc.perform(get("/auth/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.userId").value(userId.toString()))
                .andExpect(jsonPath("$.result.username").value("admin"))
                .andExpect(jsonPath("$.result.scopes[0].scopeType").value("PLANT"))
                .andExpect(jsonPath("$.result.defaultPlantId").value(plantId.toString()));
    }

    @Test
    @DisplayName("forgot-password: valid email returns 200 with the fixed enumeration-safe message")
    void forgotPassword_validEmail_returns200() throws Exception {
        mockMvc.perform(post("/auth/v1/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@erp.local"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(authService).forgotPassword(any(ForgotPasswordRequest.class));
    }

    @Test
    @DisplayName("forgot-password: malformed email fails @Valid before reaching the service")
    void forgotPassword_malformedEmail_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/auth/v1/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("reset-password: valid token returns 200 with null result")
    void resetPassword_validToken_returns200() throws Exception {
        mockMvc.perform(post("/auth/v1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"reset-tok-1","newPassword":"NewPassw0rd!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("reset-password: invalid/expired token returns 401 RESET_TOKEN_INVALID")
    void resetPassword_invalidToken_returns401ResetTokenInvalid() throws Exception {
        doThrow(new AppException(AuthErrorCode.RESET_TOKEN_INVALID))
                .when(authService).resetPassword(any(ResetPasswordRequest.class), any());

        mockMvc.perform(post("/auth/v1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"bad-tok","newPassword":"NewPassw0rd!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.RESET_TOKEN_INVALID.code()));
    }

    @Test
    @DisplayName("reset-password: password shorter than 8 chars fails @Valid before reaching the service")
    void resetPassword_shortPassword_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/auth/v1/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"reset-tok-1","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));

        verifyNoInteractions(authService);
    }
}
