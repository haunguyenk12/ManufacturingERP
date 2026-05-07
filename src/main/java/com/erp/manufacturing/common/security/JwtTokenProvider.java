package com.erp.manufacturing.common.security;

import com.erp.manufacturing.config.JwtProperties;
import com.erp.manufacturing.common.exception.TokenExpiredException;
import com.erp.manufacturing.common.exception.TokenMalformedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT token generation and validation.
 * <p>
 * Access Token claims: {@code sub}, {@code jti}, {@code roles}, {@code iat}, {@code exp}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    // ── Token generation ────────────────────────────────────────────────────

    public String generateAccessToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(jwtProperties.accessTokenExpiryMs())))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    // ── Token validation & parsing ────────────────────────────────────────

    /**
     * Validates token signature and expiry.
     * Blacklist check is done separately in {@link JwtAuthenticationFilter}.
     *
     * @throws TokenExpiredException   if token has expired
     * @throws TokenMalformedException if token is invalid
     */
    public Claims validateAndExtractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenMalformedException();
        }
    }

    public String extractUsername(String token) {
        return validateAndExtractClaims(token).getSubject();
    }

    public String extractJti(String token) {
        return validateAndExtractClaims(token).getId();
    }

    /**
     * Extracts JTI without full validation – used when token may be expired (e.g. logout).
     */
    public String extractJtiUnchecked(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getId();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getId();
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * Extracts claims from an <em>expired</em> token without throwing on expiry.
     * The JWT signature is still fully verified – only the expiry check is bypassed.
     *
     * <p>Used exclusively by {@link JwtAuthenticationFilter} on the {@code /auth/refresh}
     * path so that clients can refresh after the access token has expired.
     *
     * @throws TokenMalformedException if the token signature is invalid or the token is unparseable
     */
    public Claims extractClaimsFromExpired(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // Signature was valid; expiry is expected here – return claims safely
            log.debug("[JWT] Extracting claims from expired token (refresh path)");
            return e.getClaims();
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenMalformedException();
        }
    }

    public long getRemainingTtlMs(String token) {
        try {
            Date expiry = validateAndExtractClaims(token).getExpiration();
            return Math.max(0, expiry.getTime() - Instant.now().toEpochMilli());
        } catch (TokenExpiredException e) {
            return 0;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
