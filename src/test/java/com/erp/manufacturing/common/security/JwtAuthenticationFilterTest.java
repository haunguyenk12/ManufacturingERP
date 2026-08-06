package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * P0 auth fix regression guard: a garbage/expired {@code Authorization} header must never be able to
 * block a genuinely {@code permitAll} endpoint (that's exactly how {@code /auth/refresh} broke), and
 * {@code /auth/logout}/{@code /auth/logout-all} must NOT be added to that bypass list since they still
 * rely on this filter to identify what to revoke.
 */
@DisplayName("JwtAuthenticationFilter tests")
class JwtAuthenticationFilterTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final org.springframework.security.core.userdetails.UserDetailsService userDetailsService =
            mock(org.springframework.security.core.userdetails.UserDetailsService.class);
    private final TokenStoreService tokenStoreService = mock(TokenStoreService.class);

    // R3: ObjectMapper carries real (de)serialization logic, so it is a real instance, not a mock.
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtTokenProvider, userDetailsService, tokenStoreService, new ObjectMapper());

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    })
    @DisplayName("bypass paths: a garbage Bearer header never blocks the request")
    void bypassPath_garbageBearerToken_neverBlocksTheRequest(String path) throws Exception {
        MockHttpServletRequest request = requestWithBearer(path, "garbage-not-a-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(200); // untouched — filter never wrote an error
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    })
    @DisplayName("bypass paths: an empty Bearer value (\"Bearer \") never blocks the request")
    void bypassPath_emptyBearerValue_neverBlocksTheRequest(String path) throws Exception {
        MockHttpServletRequest request = requestWithBearer(path, "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/logout", "/api/v1/auth/logout-all"})
    @DisplayName("logout endpoints are NOT bypassed — a malformed token still fails validation")
    void logoutPaths_areNotBypassed_malformedTokenStillFails(String path) throws Exception {
        MockHttpServletRequest request = requestWithBearer(path, "garbage-not-a-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtTokenProvider.validateAndExtractClaims("garbage-not-a-jwt"))
                .thenThrow(ExceptionFactory.unauthorized(AuthErrorCode.TOKEN_MALFORMED));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
        verify(jwtTokenProvider).validateAndExtractClaims("garbage-not-a-jwt");
    }

    @Test
    @DisplayName("non-bypass path: no token at all passes through untouched (unchanged behavior)")
    void nonBypassPath_noToken_passesThroughUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/uoms");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("non-bypass path: valid token sets SecurityContext and continues the chain")
    void nonBypassPath_validToken_setsSecurityContextAndContinues() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/v1/uoms", "valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        Claims claims = claims("alice", "jti-1");
        UserDetails userDetails = User.withUsername("alice").password("x").authorities("ROLE_ADMIN").build();
        when(jwtTokenProvider.validateAndExtractClaims("valid-token")).thenReturn(claims);
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
        verify(tokenStoreService).assertNotBlacklisted("jti-1");
    }

    @Test
    @DisplayName("non-bypass path: malformed token is rejected before the chain continues")
    void nonBypassPath_malformedToken_rejectedBeforeChainContinues() throws Exception {
        MockHttpServletRequest request = requestWithBearer("/api/v1/uoms", "garbage-not-a-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtTokenProvider.validateAndExtractClaims("garbage-not-a-jwt"))
                .thenThrow(ExceptionFactory.unauthorized(AuthErrorCode.TOKEN_MALFORMED));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(AuthErrorCode.TOKEN_MALFORMED.code());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest requestWithBearer(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private Claims claims(String subject, String jti) {
        return Jwts.claims().subject(subject).id(jti).build();
    }
}
