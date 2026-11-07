package com.erp.manufacturing.module.auth.controller;

import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.module.auth.service.AuthService;
import com.erp.manufacturing.module.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.erp.manufacturing.common.exception.ValidationErrorCode;
import com.erp.manufacturing.module.user.dto.UserResponse;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@link AdminController} (D8c) – locks the {@code {code,result,message}}
 * envelope. {@code AuthService} is mocked; the {@code hasRole('ADMIN')} check on
 * {@code adminUnlockAccount} is enforced by method-security AOP on the service, not the web layer
 * (addFilters = false here), so it is covered separately by {@code AuthMethodSecurityTest}.
 */
@WebMvcTest(controllers = AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminController – response envelope contract")
class AdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthService authService;

    @MockBean
    UserService userService;

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
    @WithMockUser(roles = "ADMIN")
    @DisplayName("unlock: returns 200 with null result (noContent envelope)")
    void unlock_returns200() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/admin/users/v1/" + userId + "/unlock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verify(authService).adminUnlockAccount(userId);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("accounts/commands CREATE: 201 with the PENDING_SETUP account and no password-ish field")
    void createAccount_returns201WithPendingAccount() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.createPendingSetup(any())).thenReturn(new UserResponse(
                id, "new.hire@omniplant.vn", "new.hire@omniplant.vn", "PENDING_SETUP", Set.of("OPERATOR"),
                Instant.parse("2026-11-06T08:00:00Z"), Instant.parse("2026-11-06T08:00:00Z")));

        mockMvc.perform(post("/admin/users/v1/accounts/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"CREATE","username":"new.hire@omniplant.vn","email":"new.hire@omniplant.vn","password":"Init1al!Pass"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.userId").value(id.toString()))
                .andExpect(jsonPath("$.result.status").value("PENDING_SETUP"))
                .andExpect(jsonPath("$.result.password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("accounts/commands CREATE: missing email or password is a 400 before the service")
    void createAccount_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/admin/users/v1/accounts/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"CREATE","username":"abc","email":"not-an-email","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.INVALID_INPUT.code()));

        verifyNoInteractions(userService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("accounts/commands CREATE: duplicate email is 409 and a weak password is 422 PASSWORD_POLICY")
    void createAccount_serviceErrors_mapToDocumentedStatuses() throws Exception {
        when(userService.createPendingSetup(any()))
                .thenThrow(ExceptionFactory.alreadyExists(ValidationErrorCode.EMAIL_ALREADY_EXISTS, "Email already in use"))
                .thenThrow(ExceptionFactory.withErrors(AuthErrorCode.PASSWORD_POLICY, Map.of("password", "must contain a digit")));
        String body = """
                {"kind":"CREATE","username":"abc","email":"abc@omniplant.vn","password":"weak"}
                """;

        mockMvc.perform(post("/admin/users/v1/accounts/commands").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ValidationErrorCode.EMAIL_ALREADY_EXISTS.code()));
        mockMvc.perform(post("/admin/users/v1/accounts/commands").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.PASSWORD_POLICY.code()))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }
}
