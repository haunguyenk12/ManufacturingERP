package com.erp.manufacturing.common.security;

import com.erp.manufacturing.config.JwtProperties;
import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.common.exception.ExceptionFactory;
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
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JwtProperties jwtProperties;

    // ── Token generation ────────────────────────────────────────────────────

    public String generateAccessToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .id(UUID.randomUUID().toString())
                .issuer(jwtProperties.issuer())
                .audience().add(jwtProperties.audience()).and()
                .claim("roles", roles)
                .claim("ver", userDetails instanceof com.erp.manufacturing.module.user.domain.UserPrincipal principal
                        ? principal.getAuthVersion() : 0L)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusMillis(jwtProperties.accessTokenExpiryMs())))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken() {
        byte[] value = new byte[32];
        SECURE_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    // ── Token validation & parsing ────────────────────────────────────────

    /**
     * Validates token signature and expiry.
     * Blacklist check is done separately in {@link JwtAuthenticationFilter}.
     *
     * @throws AppException {@link AuthErrorCode#TOKEN_EXPIRED}   if token has expired
     * @throws AppException {@link AuthErrorCode#TOKEN_MALFORMED} if token is invalid
     */
    public Claims validateAndExtractClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!jwtProperties.issuer().equals(claims.getIssuer())
                    || claims.getAudience() == null
                    || !claims.getAudience().contains(jwtProperties.audience())) {
                throw ExceptionFactory.unauthorized(AuthErrorCode.TOKEN_MALFORMED);
            }
            return claims;
        } catch (ExpiredJwtException e) {
            throw ExceptionFactory.unauthorized(AuthErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw ExceptionFactory.unauthorized(AuthErrorCode.TOKEN_MALFORMED);
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

    public long getRemainingTtlMs(String token) {
        try {
            Date expiry = validateAndExtractClaims(token).getExpiration();
            return Math.max(0, expiry.getTime() - Instant.now().toEpochMilli());
        } catch (AppException e) {
            // TOKEN_EXPIRED or TOKEN_MALFORMED – remaining TTL is 0
            return 0;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
