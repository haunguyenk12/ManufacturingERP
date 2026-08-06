package com.erp.manufacturing.common.security;

import com.erp.manufacturing.common.exception.AppException;
import com.erp.manufacturing.common.exception.AuthErrorCode;
import com.erp.manufacturing.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtTokenProvider}.
 *
 * <p>The provider depends only on {@link JwtProperties} – no Redis, no Spring context –
 * so a real instance is used (D1). Signing/verifying is exactly what is under test,
 * therefore JJWT is never mocked.
 *
 * <p>Expiry is exercised by building a provider with a <em>negative</em>
 * {@code accessTokenExpiryMs}, which yields an already-expired token deterministically
 * without {@code Thread.sleep} (D3).
 */
@DisplayName("JwtTokenProvider Unit Tests")
class JwtTokenProviderTest {

    private static final String SECRET       = "test-secret-key-that-is-at-least-256-bits-long!!";
    private static final String OTHER_SECRET = "another-secret-key-also-at-least-256-bits-long!!";

    private static final long VALID_EXPIRY_MS   = 900_000L;
    private static final long EXPIRED_EXPIRY_MS = -1_000L;
    private static final long REFRESH_EXPIRY_MS   = 604_800_000L;
    private static final long ABSOLUTE_TIMEOUT_MS = 2_592_000_000L;

    private JwtTokenProvider provider(long accessTokenExpiryMs) {
        return provider(SECRET, accessTokenExpiryMs);
    }

    private JwtTokenProvider provider(String secret, long accessTokenExpiryMs) {
        return new JwtTokenProvider(
                new JwtProperties(secret, accessTokenExpiryMs, REFRESH_EXPIRY_MS, ABSOLUTE_TIMEOUT_MS));
    }

    private UserDetails user() {
        return User.withUsername("alice")
                .password("x")
                .authorities("ROLE_ADMIN", "PERM_BOM_MANAGE")
                .build();
    }

    // ── Generation ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Generate then validate – subject, roles claim and jti round-trip")
    void generateThenValidate_roundTrip() {
        JwtTokenProvider provider = provider(VALID_EXPIRY_MS);

        String token = provider.generateAccessToken(user());
        Claims claims = provider.validateAndExtractClaims(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertThat(roles).containsExactlyInAnyOrder("ROLE_ADMIN", "PERM_BOM_MANAGE");
        assertThat(claims.getId()).isNotBlank();

        // Convenience extractors delegate to validateAndExtractClaims
        assertThat(provider.extractUsername(token)).isEqualTo("alice");
        assertThat(provider.extractJti(token)).isEqualTo(claims.getId());
    }

    @Test
    @DisplayName("Two access tokens for the same user have different jti")
    void generateAccessToken_twoCalls_haveDifferentJti() {
        JwtTokenProvider provider = provider(VALID_EXPIRY_MS);
        UserDetails userDetails = user();

        String jti1 = provider.extractJti(provider.generateAccessToken(userDetails));
        String jti2 = provider.extractJti(provider.generateAccessToken(userDetails));

        assertThat(jti1).isNotEqualTo(jti2);
    }

    @Test
    @DisplayName("Refresh tokens are unique random values")
    void generateRefreshToken_twoCalls_areDifferent() {
        JwtTokenProvider provider = provider(VALID_EXPIRY_MS);

        assertThat(provider.generateRefreshToken()).isNotEqualTo(provider.generateRefreshToken());
    }

    // ── Validation failures ────────────────────────────────────────────────

    @Test
    @DisplayName("Validate expired token – throws AppException TOKEN_EXPIRED")
    void validate_expiredToken_throwsTokenExpired() {
        JwtTokenProvider expiredProvider = provider(EXPIRED_EXPIRY_MS);
        String token = expiredProvider.generateAccessToken(user());

        assertThatThrownBy(() -> expiredProvider.validateAndExtractClaims(token))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("Validate malformed token – throws AppException TOKEN_MALFORMED")
    void validate_malformedToken_throwsTokenMalformed() {
        JwtTokenProvider provider = provider(VALID_EXPIRY_MS);

        assertThatThrownBy(() -> provider.validateAndExtractClaims("abc.def.ghi"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.TOKEN_MALFORMED));
    }

    @Test
    @DisplayName("Validate token signed with another secret – throws AppException TOKEN_MALFORMED")
    void validate_wrongSignature_throwsTokenMalformed() {
        String foreignToken = provider(OTHER_SECRET, VALID_EXPIRY_MS).generateAccessToken(user());
        JwtTokenProvider provider = provider(VALID_EXPIRY_MS);

        assertThatThrownBy(() -> provider.validateAndExtractClaims(foreignToken))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(AuthErrorCode.TOKEN_MALFORMED));
    }

    // ── Expired-token access paths (logout / refresh) ──────────────────────

    @Test
    @DisplayName("extractJtiUnchecked – expired token still yields its jti")
    void extractJtiUnchecked_expiredToken_stillReturnsJti() {
        JwtTokenProvider expiredProvider = provider(EXPIRED_EXPIRY_MS);
        String token = expiredProvider.generateAccessToken(user());

        assertThat(expiredProvider.extractJtiUnchecked(token)).isNotBlank();
    }

    @Test
    @DisplayName("extractJtiUnchecked – wrong signature returns null, does not throw")
    void extractJtiUnchecked_wrongSignature_returnsNull() {
        String foreignToken = provider(OTHER_SECRET, VALID_EXPIRY_MS).generateAccessToken(user());

        assertThat(provider(VALID_EXPIRY_MS).extractJtiUnchecked(foreignToken)).isNull();
    }

    // ── Remaining TTL ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getRemainingTtlMs – valid token returns a positive TTL within the configured expiry")
    void getRemainingTtlMs_validToken_positive() {
        JwtTokenProvider provider = provider(VALID_EXPIRY_MS);
        String token = provider.generateAccessToken(user());

        assertThat(provider.getRemainingTtlMs(token))
                .isPositive()
                .isLessThanOrEqualTo(VALID_EXPIRY_MS);
    }

    @Test
    @DisplayName("getRemainingTtlMs – expired token returns 0 instead of throwing")
    void getRemainingTtlMs_expiredToken_zero() {
        JwtTokenProvider expiredProvider = provider(EXPIRED_EXPIRY_MS);
        String token = expiredProvider.generateAccessToken(user());

        assertThat(expiredProvider.getRemainingTtlMs(token)).isZero();
    }

    @Test
    @DisplayName("getRemainingTtlMs – malformed token returns 0 instead of throwing")
    void getRemainingTtlMs_malformedToken_zero() {
        assertThat(provider(VALID_EXPIRY_MS).getRemainingTtlMs("abc.def.ghi")).isZero();
    }
}
