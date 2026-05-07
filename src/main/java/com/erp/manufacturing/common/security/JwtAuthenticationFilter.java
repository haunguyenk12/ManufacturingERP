package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.AuthException;
import com.erp.manufacturing.common.exception.TokenMalformedException;
import com.erp.manufacturing.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter – validates Bearer token and populates SecurityContext.
 *
 * <h3>Refresh-path special handling</h3>
 * <p>The {@code /api/v1/auth/refresh} endpoint intentionally accepts <em>expired</em>
 * access tokens. When the request targets that path, this filter extracts the subject
 * claim from the token even if it is expired, but still verifies the signature.
 * The actual refresh-token validity check is done in {@link
 * com.erp.manufacturing.module.auth.service.AuthService#refresh}.
 *
 * <h3>On valid (or expired-but-refresh-path) token:</h3>
 * <ol>
 *   <li>Populates {@link SecurityContextHolder} (only for non-expired tokens)</li>
 *   <li>Sets {@code authenticatedUserId} request attribute (used by RateLimitFilter + AuditLog)</li>
 *   <li>Puts {@code userId} into MDC (all subsequent log lines carry userId)</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX   = "Bearer ";
    private static final String REFRESH_PATH    = "/api/v1/auth/refresh";

    private final JwtTokenProvider    jwtTokenProvider;
    private final UserDetailsService  userDetailsService;
    private final TokenStoreService   tokenStoreService;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isRefreshPath = REFRESH_PATH.equals(request.getRequestURI());

        try {
            if (isRefreshPath) {
                handleRefreshPath(request, token);
            } else {
                handleNormalPath(request, token);
            }
        } catch (AuthException ex) {
            // Write error response directly – filter runs before Spring Security dispatcher
            writeAuthError(response, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }

    // ── Normal path: full validation ─────────────────────────────────────

    /**
     * Standard flow: validates signature + expiry, checks blacklist, populates SecurityContext.
     * Throws {@link AuthException} on any failure (token expired, malformed, revoked).
     */
    private void handleNormalPath(HttpServletRequest request, String token) {
        var claims = jwtTokenProvider.validateAndExtractClaims(token); // throws on expired/invalid
        String jti      = claims.getId();
        String username = claims.getSubject();

        // Blacklist check
        tokenStoreService.assertNotBlacklisted(jti);

        // Load user and set SecurityContext
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        var authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        enrichRequest(request, username, jti);
    }

    // ── Refresh path: allow expired token ────────────────────────────────

    /**
     * Refresh-specific flow:
     * <ul>
     *   <li>Extracts claims from the token even if expired (signature still verified).</li>
     *   <li>Does NOT set SecurityContext (token is expired, user is not "authenticated").</li>
     *   <li>Only sets {@code authenticatedUserId} so {@link
     *       com.erp.manufacturing.module.auth.service.AuthService#refresh} can identify the user.</li>
     * </ul>
     *
     * <p>The actual security check (valid refresh token + tokenId) happens inside AuthService.
     */
    private void handleRefreshPath(HttpServletRequest request, String token) {
        Claims claims = extractClaimsAllowExpired(token);   // throws only on signature failure
        String username = claims.getSubject();

        // Do NOT set SecurityContext – token is expired. Just identify the user.
        enrichRequest(request, username, claims.getId());
        log.debug("[JWT] Refresh path – extracted subject from (possibly expired) token: user={}", username);
    }

    /**
     * Parses JWT claims regardless of expiry. Still verifies the signature.
     *
     * @throws TokenMalformedException if the signature is invalid or token is unparseable
     */
    private Claims extractClaimsAllowExpired(String token) {
        try {
            return jwtTokenProvider.validateAndExtractClaims(token);
        } catch (com.erp.manufacturing.common.exception.TokenExpiredException e) {
            // Token expired but signature was valid – extract claims from the exception
            return jwtTokenProvider.extractClaimsFromExpired(token);
        }
        // TokenMalformedException propagates up → caught by caller → writeAuthError
    }

    // ── Shared helpers ────────────────────────────────────────────────────

    private void enrichRequest(HttpServletRequest request, String username, String jti) {
        request.setAttribute("authenticatedUserId", username);
        request.setAttribute("authenticatedJti",    jti);
        MDC.put("userId", username);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void writeAuthError(HttpServletResponse response, AuthException ex) throws IOException {
        response.setStatus(ex.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }
}
