package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.audit.AuditLogService;
import com.erp.manufacturing.common.exception.BusinessErrorCode;
import com.erp.manufacturing.config.RateLimitProperties;
import com.erp.manufacturing.config.RateLimitProperties.Action;
import com.erp.manufacturing.config.RateLimitProperties.RateLimitRule;
import com.erp.manufacturing.config.RateLimitProperties.Scope;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the security filter chain.
 *
 * <p>Verifies filter execution order and behavior at the filter-chain level:
 * <ol>
 *   <li>Unauthenticated request → IP rate-limit is evaluated, USER rate-limit is skipped
 *       (no {@code authenticatedUserId}). Result: 401, NOT 429.</li>
 *   <li>Request with valid JWT → {@code authenticatedUserId} is set →
 *       USER rate-limit filter runs and allows through. Result: 200.</li>
 *   <li>USER rate-limit blocks when per-user counter exceeds the configured limit.
 *       Result: 429 with {@code X-RateLimit-*} headers and correct error code.</li>
 * </ol>
 *
 * <h3>Filter order under test</h3>
 * <pre>
 *   TraceIdFilter (1) → RateLimitFilter/IP (2) → JwtAuthenticationFilter (3) → UserRateLimitFilter (4)
 * </pre>
 *
 * <p><strong>Approach</strong>: {@link WebMvcTest} with only the filter + security beans imported.
 * Infrastructure (Redis, JWT, UserDetails) is mocked so no live services are needed.
 * {@link IpExtractor} is also mocked to avoid {@code @PostConstruct} CIDR parsing issues.
 */
@WebMvcTest(controllers = SecurityTestController.class)
@Import({
        com.erp.manufacturing.config.SecurityConfig.class,
        TraceIdFilter.class,
        RateLimitFilter.class,
        JwtAuthenticationFilter.class,
        UserRateLimitFilter.class,
        JwtAuthEntryPoint.class,
        JwtAccessDeniedHandler.class,
})
@DisplayName("Security filter chain – order and scope tests")
class SecurityFilterChainTest {

    // ── Autowired ─────────────────────────────────────────────────────────

    @Autowired MockMvc mockMvc;

    // ── Mocked infrastructure ─────────────────────────────────────────────

    @MockBean RedisTemplate<String, String>                   redisTemplate;
    @MockBean JwtTokenProvider                                jwtTokenProvider;
    @MockBean TokenStoreService                               tokenStoreService;
    @MockBean UserDetailsService                              userDetailsService;
    @MockBean AuditLogService                                 auditLogService;
    @MockBean RateLimitProperties                             rateLimitProperties;
    @MockBean IpExtractor                                     ipExtractor;
    @MockBean com.erp.manufacturing.config.SecurityProperties securityProperties;

    // ── Redis operation mocks (recreated per test) ────────────────────────

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps;
    @SuppressWarnings("unchecked")
    private HashOperations<String, Object, Object> hashOps;

    // ── Reusable rule constants ───────────────────────────────────────────

    private static final RateLimitRule IP_RULE =
            new RateLimitRule("global-ip", Scope.IP, "/**", 200, 60, Action.BLOCK);

    private static final RateLimitRule USER_RULE =
            new RateLimitRule("global-user", Scope.USER, "/**", 500, 60, Action.BLOCK);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpRedisDefaults() {
        valueOps = mock(ValueOperations.class);
        hashOps  = mock(HashOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        // Safe defaults: no blacklist, no whitelist, no override, counters under limit
        when(valueOps.get(anyString())).thenReturn(null);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(hashOps.get(anyString(), anyString())).thenReturn(null);
        when(valueOps.increment(anyString())).thenReturn(1L);

        // IpExtractor always returns a stable IP for unit tests
        when(ipExtractor.extract(any(HttpServletRequest.class))).thenReturn("127.0.0.1");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test 1 – Unauthenticated: IP rate-limit runs, USER rate-limit skipped
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Without an Authorization header:
     * <ul>
     *   <li>{@link RateLimitFilter} runs (IP-scope) → counter=1, limit=200 → passes</li>
     *   <li>{@link JwtAuthenticationFilter} is a no-op (no Bearer token)</li>
     *   <li>{@link UserRateLimitFilter} sees {@code authenticatedUserId == null} → no-op</li>
     *   <li>Spring Security rejects the unauthenticated request with 401</li>
     * </ul>
     * If USER rate-limit had incorrectly run and blocked, we'd get 429 instead of 401.
     */
    @Test
    @DisplayName("Unauthenticated: IP rate-limit evaluates, USER rate-limit skipped → 401 (not 429)")
    void unauthenticated_ipRateLimitApplies_userRateLimitSkipped_returns401() throws Exception {
        when(rateLimitProperties.enabled()).thenReturn(true);
        when(rateLimitProperties.rules()).thenReturn(List.of(IP_RULE));

        mockMvc.perform(get("/api/v1/test/ping"))
               .andExpect(status().isUnauthorized()); // 401 proves USER rate-limit did NOT block

        verify(valueOps).increment(argThat(k -> k != null && k.contains("global-ip")));
        verify(valueOps, never()).increment(argThat(k -> k != null && k.contains("global-user")));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test 2 – Authenticated: JWT sets userId, USER rate-limit filter runs
    // ─────────────────────────────────────────────────────────────────────

    /**
     * With a valid Bearer token:
     * <ul>
     *   <li>{@link RateLimitFilter} runs (IP-scope) → passes</li>
     *   <li>{@link JwtAuthenticationFilter} validates JWT → sets {@code authenticatedUserId = "alice"}</li>
     *   <li>{@link UserRateLimitFilter} sees {@code authenticatedUserId} → evaluates USER rules → passes</li>
     *   <li>Controller returns 200</li>
     * </ul>
     */
    @Test
    @DisplayName("Authenticated: JWT sets authenticatedUserId, USER rate-limit runs and allows → 200")
    void authenticated_jwtValid_userRateLimitAllows_returns200() throws Exception {
        when(rateLimitProperties.enabled()).thenReturn(true);
        when(rateLimitProperties.rules()).thenReturn(List.of(IP_RULE, USER_RULE));

        stubValidJwt("alice", "jti-alice", "valid.jwt.alice");

        mockMvc.perform(get("/api/v1/test/ping")
                        .header("Authorization", "Bearer valid.jwt.alice"))
               .andExpect(status().isOk());

        verify(valueOps).increment(argThat(k -> k != null && k.contains("global-ip")));
        verify(valueOps).increment(argThat(k -> k != null && k.contains("global-user")));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test 3 – USER rate-limit blocks: 429 with headers + error code
    // ─────────────────────────────────────────────────────────────────────

    /**
     * When the per-user counter exceeds the limit:
     * <ul>
     *   <li>{@link UserRateLimitFilter} returns 429 before the request reaches the controller</li>
     *   <li>Response includes {@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining},
     *       {@code Retry-After} headers and the {@code RATE_LIMIT_EXCEEDED} error code</li>
     * </ul>
     */
    @Test
    @DisplayName("USER rate-limit exceeded → 429 with X-RateLimit-* headers and RATE_LIMIT_EXCEEDED code")
    void authenticated_userRateLimitExceeded_returns429WithHeaders() throws Exception {
        // Tight USER rule: limit=1; counter will return 2 → blocked
        RateLimitRule tightUserRule =
                new RateLimitRule("global-user", Scope.USER, "/**", 1, 60, Action.BLOCK);

        when(rateLimitProperties.enabled()).thenReturn(true);
        when(rateLimitProperties.rules()).thenReturn(List.of(IP_RULE, tightUserRule));

        // IP counter = 1 (under limit of 200)
        when(valueOps.increment(argThat(k -> k != null && k.contains("global-ip"))))
                .thenReturn(1L);
        // USER counter = 2 (exceeds limit = 1)
        when(valueOps.increment(argThat(k -> k != null && k.contains("global-user"))))
                .thenReturn(2L);

        stubValidJwt("bob", "jti-bob", "valid.jwt.bob");

        mockMvc.perform(get("/api/v1/test/ping")
                        .header("Authorization", "Bearer valid.jwt.bob"))
               .andExpect(status().isTooManyRequests())         // 429
               .andExpect(header().exists("X-RateLimit-Limit"))
               .andExpect(header().exists("X-RateLimit-Remaining"))
               .andExpect(header().exists("Retry-After"))
               .andExpect(jsonPath("$.code").value(BusinessErrorCode.RATE_LIMIT_EXCEEDED.code()));
    }

    // ── Test helpers ──────────────────────────────────────────────────────

    /**
     * Stubs JWT validation so {@code "Bearer {rawToken}"} passes all checks and sets
     * {@code authenticatedUserId = username} in the request attribute.
     */
    private void stubValidJwt(String username, String jti, String rawToken) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(username);
        when(claims.getId()).thenReturn(jti);
        when(jwtTokenProvider.validateAndExtractClaims(rawToken)).thenReturn(claims);

        // assertNotBlacklisted is void; default mock does nothing (token is not revoked)
        doNothing().when(tokenStoreService).assertNotBlacklisted(jti);

        User userDetails = new User(username, "ignored", Collections.emptyList());
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
    }
}
