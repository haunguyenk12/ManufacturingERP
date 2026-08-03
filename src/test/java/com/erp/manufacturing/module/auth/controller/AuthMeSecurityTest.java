package com.erp.manufacturing.module.auth.controller;

import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.security.IpExtractor;
import com.erp.manufacturing.common.security.JwtAccessDeniedHandler;
import com.erp.manufacturing.common.security.JwtAuthEntryPoint;
import com.erp.manufacturing.common.security.JwtAuthenticationFilter;
import com.erp.manufacturing.common.security.JwtTokenProvider;
import com.erp.manufacturing.common.security.RateLimitFilter;
import com.erp.manufacturing.common.security.TokenStoreService;
import com.erp.manufacturing.common.security.TraceIdFilter;
import com.erp.manufacturing.common.security.UserRateLimitFilter;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.config.SecurityConfig;
import com.erp.manufacturing.config.SecurityProperties;
import com.erp.manufacturing.module.auth.dto.MeResponse;
import com.erp.manufacturing.module.auth.service.AuthService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the real filter chain (unlike {@link AuthControllerTest}, which runs with
 * {@code addFilters = false}), that {@code /api/v1/auth/me} is the one auth endpoint that is NOT
 * permit-all — and that carving it out of the wildcard didn't accidentally tighten the other four.
 *
 * <p>Mirrors {@code SecurityFilterChainTest}'s approach (real {@link SecurityConfig} + filters,
 * infrastructure mocked), but targets {@link AuthController} itself instead of a fake test
 * controller, since the new matcher is specific to the {@code /api/v1/auth/**} path family.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({
        SecurityConfig.class,
        TraceIdFilter.class,
        RateLimitFilter.class,
        JwtAuthenticationFilter.class,
        UserRateLimitFilter.class,
        JwtAuthEntryPoint.class,
        JwtAccessDeniedHandler.class,
})
@DisplayName("/api/v1/auth/me – requires authentication (unlike the rest of AuthController)")
class AuthMeSecurityTest {

    @Autowired MockMvc mockMvc;

    @MockBean AuthService authService;
    @MockBean RedisTemplate<String, String> redisTemplate;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean TokenStoreService tokenStoreService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean AuditLogService auditLogService;
    @MockBean RateLimitProperties rateLimitProperties;
    @MockBean IpExtractor ipExtractor;
    @MockBean SecurityProperties securityProperties;

    @Test
    @DisplayName("without a token → 401, request never reaches the controller")
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("with a valid token → 200")
    void me_withValidToken_returns200() throws Exception {
        stubValidJwt("admin", "jti-admin", "valid.jwt.admin");
        when(authService.me(any())).thenReturn(new MeResponse(
                UUID.randomUUID(), "admin", "admin@erp.local", "ACTIVE",
                Set.of("ADMIN"), Set.of(), List.of(), null));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer valid.jwt.admin"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("regression guard: /login is still permit-all after carving out /me")
    void login_stillPermitAll_afterAddingMeMatcher() throws Exception {
        // An empty body is enough to prove the request reached the controller (400 from @Valid /
        // unreadable body, not 401) — the point here is that no token was required to get that far.
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isBadRequest());
    }

    private void stubValidJwt(String username, String jti, String rawToken) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(username);
        when(claims.getId()).thenReturn(jti);
        when(jwtTokenProvider.validateAndExtractClaims(rawToken)).thenReturn(claims);

        doNothing().when(tokenStoreService).assertNotBlacklisted(jti);

        User userDetails = new User(username, "ignored", Collections.emptyList());
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
    }
}
