package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;

/**
 * JWT authentication filter – validates Bearer token and populates SecurityContext.
 *
 * <h3>Bypass paths (P0 auth fix)</h3>
 * <p>{@link #BYPASS_PATHS} are genuinely {@code permitAll} endpoints that never need a valid access
 * token to do their job. For these, this filter behaves exactly as if no {@code Authorization} header
 * were present at all — it does not even attempt to parse one, regardless of what the header contains.
 * This matters because a client that always attaches whatever token it has in storage (even an empty,
 * stale, or corrupted one) must not be able to turn a {@code permitAll} endpoint into a 401 wall; that
 * previously happened to {@code /api/v1/auth/refresh} specifically, which is the one bypass-list entry
 * that most needs it — refresh is the documented recovery path for exactly the case where the access
 * token is unusable, so it cannot itself depend on that same token being parseable. It no longer does:
 * {@link com.erp.manufacturing.module.auth.service.AuthService#refresh} resolves the caller's identity
 * from {@code tokenId} alone via {@link TokenStoreService#getTokenOwner}, not from this filter.
 *
 * <p>🔴 {@code /api/v1/auth/logout} and {@code /api/v1/auth/logout-all} are deliberately <b>not</b> in
 * the bypass list, even though they are also {@code permitAll}: unlike the endpoints above, {@code
 * AuthService.logout}/{@code logoutAllDevices} still identify what to revoke via the {@code
 * authenticatedUserId} attribute this filter sets from a successfully-parsed token. Bypassing them
 * would make logout silently stop revoking anything whenever a token is present but unparseable.
 *
 * <h3>On valid token:</h3>
 * <ol>
 *   <li>Populates {@link SecurityContextHolder}</li>
 *   <li>Sets {@code authenticatedUserId} request attribute (used by RateLimitFilter + AuditLog)</li>
 *   <li>Puts {@code userId} into MDC (all subsequent log lines carry userId)</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /** Genuinely permitAll endpoints that never need this filter to touch the Authorization header. */
    private static final Set<String> BYPASS_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password");

    private final JwtTokenProvider    jwtTokenProvider;
    private final UserDetailsService  userDetailsService;
    private final TokenStoreService   tokenStoreService;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (BYPASS_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            handleNormalPath(request, token);
        } catch (AppException ex) {
            // Write error response directly – filter runs before Spring Security dispatcher
            writeAuthError(response, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Validates signature + expiry, checks blacklist, populates SecurityContext.
     * Throws {@link AppException} on any failure (token expired, malformed, revoked).
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

    private void writeAuthError(HttpServletResponse response, AppException ex) throws IOException {
        response.setStatus(ex.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }
}
