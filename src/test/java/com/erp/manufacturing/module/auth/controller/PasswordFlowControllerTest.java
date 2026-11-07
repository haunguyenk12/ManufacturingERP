package com.erp.manufacturing.module.auth.controller;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.RetryAfterException;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.auth.dto.ChallengeResponse;
import com.erp.manufacturing.module.auth.dto.LoginRequest;
import com.erp.manufacturing.module.auth.dto.PasswordSetupResponse;
import com.erp.manufacturing.module.auth.dto.VerifyChallengeResponse;
import com.erp.manufacturing.module.auth.service.AuthService;
import com.erp.manufacturing.module.auth.service.PasswordFlowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wire contract of the first-login setup branch, the email-code endpoints and password change
 * (FE_REQUEST_ACCOUNT_PASSWORD_FLOWS_2026-11-06): envelope shape, HTTP status per {@code code}, the
 * {@code Retry-After} header, and the absence of any token on the PASSWORD_SETUP login answer.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController – password setup / challenge / change contract")
class PasswordFlowControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthService authService;
    @MockBean
    PasswordFlowService passwordFlowService;

    // Required only so the auto-detected security filters can be constructed by the slice.
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

    private static final String VERIFY = "/auth/v1/password-challenges/verify";
    private static final String COMPLETE = "/auth/v1/password-challenges/complete";

    @Test
    @DisplayName("login: PENDING_SETUP answers kind=PASSWORD_SETUP with the challenge and NO token fields")
    void login_pendingSetup_returnsChallengeAndNoTokens() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.login(any(LoginRequest.class), any())).thenReturn(new PasswordSetupResponse(
                "new.hire@omniplant.vn",
                new ChallengeResponse(id, Instant.parse("2026-11-06T08:10:00Z"), Instant.parse("2026-11-06T08:00:30Z"))));

        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new.hire","password":"Init1al!Pass"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.kind").value("PASSWORD_SETUP"))
                .andExpect(jsonPath("$.result.email").value("new.hire@omniplant.vn"))
                .andExpect(jsonPath("$.result.challenge.id").value(id.toString()))
                .andExpect(jsonPath("$.result.challenge.expiresAt").value("2026-11-06T08:10:00Z"))
                .andExpect(jsonPath("$.result.challenge.resendAt").value("2026-11-06T08:00:30Z"))
                .andExpect(jsonPath("$.result.accessToken").doesNotExist())
                .andExpect(jsonPath("$.result.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.result.tokenId").doesNotExist());
    }

    @Test
    @DisplayName("password-challenges: returns the challenge envelope")
    void requestChallenge_returnsChallenge() throws Exception {
        UUID id = UUID.randomUUID();
        when(passwordFlowService.request(any())).thenReturn(
                new ChallengeResponse(id, Instant.parse("2026-11-06T08:10:00Z"), Instant.parse("2026-11-06T08:00:30Z")));

        mockMvc.perform(post("/auth/v1/password-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"new.hire","purpose":"SETUP","previousId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(id.toString()))
                .andExpect(jsonPath("$.result.expiresAt").value("2026-11-06T08:10:00Z"));
    }

    @Test
    @DisplayName("password-challenges: blank identifier or unknown purpose is a 400 before the service")
    void requestChallenge_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/auth/v1/password-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"","purpose":"SETUP"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));
        mockMvc.perform(post("/auth/v1/password-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"a","purpose":"HACK"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordFlowService);
    }

    @Test
    @DisplayName("password-challenges: RESEND_TOO_SOON is 429 with a Retry-After header and no result")
    void requestChallenge_tooSoon_returns429WithRetryAfter() throws Exception {
        when(passwordFlowService.request(any())).thenThrow(new RetryAfterException(AuthErrorCode.RESEND_TOO_SOON, 17));

        mockMvc.perform(post("/auth/v1/password-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"new.hire","purpose":"SETUP","previousId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "17"))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.RESEND_TOO_SOON.code()))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("password-challenges: SETUP without proof of the initial login is 403 PASSWORD_SETUP_REQUIRED")
    void requestChallenge_withoutInitialLogin_returns403() throws Exception {
        when(passwordFlowService.request(any())).thenThrow(new AppException(AuthErrorCode.PASSWORD_SETUP_REQUIRED));

        mockMvc.perform(post("/auth/v1/password-challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"new.hire","purpose":"SETUP"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.PASSWORD_SETUP_REQUIRED.code()));
    }

    @Test
    @DisplayName("verify: returns the setup token under result.setupToken")
    void verifyChallenge_returnsSetupToken() throws Exception {
        when(passwordFlowService.verify(any())).thenReturn(new VerifyChallengeResponse("tok-123"));

        mockMvc.perform(post(VERIFY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"%s","code":"123456"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.setupToken").value("tok-123"));
    }

    @Test
    @DisplayName("verify: CODE_INVALID is 400, CODE_EXPIRED and CODE_USED are 422, CODE_ATTEMPTS is 429 with Retry-After")
    void verifyChallenge_codeErrors_mapToDocumentedStatuses() throws Exception {
        String body = """
                {"challengeId":"%s","code":"000000"}
                """.formatted(UUID.randomUUID());
        when(passwordFlowService.verify(any()))
                .thenThrow(new AppException(AuthErrorCode.CODE_INVALID))
                .thenThrow(new AppException(AuthErrorCode.CODE_EXPIRED))
                .thenThrow(new AppException(AuthErrorCode.CODE_USED))
                .thenThrow(new RetryAfterException(AuthErrorCode.CODE_ATTEMPTS, 300));

        mockMvc.perform(post(VERIFY).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.CODE_INVALID.code()));
        mockMvc.perform(post(VERIFY).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.CODE_EXPIRED.code()));
        mockMvc.perform(post(VERIFY).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.CODE_USED.code()));
        mockMvc.perform(post(VERIFY).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "300"))
                .andExpect(jsonPath("$.code").value(AuthErrorCode.CODE_ATTEMPTS.code()));
    }

    @Test
    @DisplayName("complete: success is 200 and carries no result")
    void completeChallenge_success_returnsNoResult() throws Exception {
        mockMvc.perform(post(COMPLETE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"setupToken":"tok-123","password":"Str0ng!Passw0rd"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("complete: PASSWORD_POLICY is 422 with the failing field named; SETUP_TOKEN_INVALID is 422")
    void completeChallenge_errors_mapToDocumentedStatuses() throws Exception {
        doThrow(ExceptionFactory.withErrors(AuthErrorCode.PASSWORD_POLICY, Map.of("password", "must contain a digit")))
                .doThrow(new AppException(AuthErrorCode.SETUP_TOKEN_INVALID))
                .when(passwordFlowService).complete(any(), any(), any());
        String body = """
                {"setupToken":"tok-123","password":"weakpassword"}
                """;

        mockMvc.perform(post(COMPLETE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.PASSWORD_POLICY.code()))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        mockMvc.perform(post(COMPLETE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.SETUP_TOKEN_INVALID.code()));
    }

    @Test
    @DisplayName("change: hands the authenticated username to the service and answers 200; wrong current password is 422")
    void changePassword_usesAuthenticatedUser() throws Exception {
        String body = """
                {"currentPassword":"Curr3nt!Pass","password":"Str0ng!Passw0rd"}
                """;

        mockMvc.perform(post("/auth/v1/password/change")
                        .requestAttr("authenticatedUserId", "old.hand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());
        verify(passwordFlowService).change(eq("old.hand"), any(), any(), any());

        doThrow(new AppException(AuthErrorCode.CURRENT_PASSWORD_INVALID))
                .when(passwordFlowService).change(any(), any(), any(), any());
        mockMvc.perform(post("/auth/v1/password/change")
                        .requestAttr("authenticatedUserId", "old.hand")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.CURRENT_PASSWORD_INVALID.code()));
    }
}
